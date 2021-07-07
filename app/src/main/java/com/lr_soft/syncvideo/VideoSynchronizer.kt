package com.lr_soft.syncvideo

import android.app.Activity
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Surface
import android.view.TextureView
import androidx.preference.PreferenceManager
import com.lyft.kronos.AndroidClockFactory
import com.lyft.kronos.KronosClock
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue


class VideoSynchronizer(
    private val activity: Activity,
    private val clientOrServerSelector: ClientServerSelector,
    foregroundTextureView: TextureView,
    backgroundTextureView: TextureView
) {
    private val syncVideoPlayer = SyncVideoPlayer(foregroundTextureView, backgroundTextureView)
    private lateinit var kronosClock: KronosClock
    private val fileManager = FileManager(activity.applicationContext)
    private val preferences = PreferenceManager.getDefaultSharedPreferences(activity)
    private lateinit var timer: Timer
    private var isRunning = false
    // Statistics for better synchronization.
    // The amount of milliseconds that seekTo is offset by.
    private val seekBiasMs = AverageInt(initialValue = 0, oldCoefficient = 0.7)
    private val averageLagMs = AverageInt(initialValue = 0)
    private var synchronizedLastTime = false

    fun start() {
        isRunning = true
        kronosClock = AndroidClockFactory.createKronosClock(activity.applicationContext)
        kronosClock.syncInBackground()
        timer = Timer()
        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                synchronizeVideo()
            }
        }, 0, 500)
    }

    fun stop() {
        isRunning = false
        kronosClock.shutdown()
        timer.cancel()
        syncVideoPlayer.stop()
    }

    private fun synchronizeVideo() {
        val clientOrServer = clientOrServerSelector.clientOrServer ?: return
        var schedule = clientOrServer.schedule ?: return  // Potentially expensive line
        val deviceIdKey = activity.getString(R.string.pref_device_id_key)
        val deviceId =
            preferences.getString(deviceIdKey, null) ?: return Logger.log("Empty deviceId!")
        schedule = schedule.filterByDeviceId(deviceId)
        checkVideosPresence(schedule, clientOrServer)
        activity.runOnUiThread { synchronizeVideoUiThread(schedule) }
    }

    private fun checkVideosPresence(schedule: Schedule, clientOrServer: ClientOrServer) {
        val videoFilenames = schedule.scheduledVideos.map { it.filename }.toSet()
        for (filename in videoFilenames) {
            if (fileManager.getFile(filename) == null) {
                clientOrServer.handleMissingFile(filename)
            }
        }
    }

    private fun synchronizeVideoUiThread(schedule: Schedule) {
        if (!isRunning) {
            return
        }
        if (schedule.scheduledVideos.isEmpty()) {
            syncVideoPlayer.stop()
            return
        }

        val currentTimeMs = kronosClock.getCurrentTimeMs()
        val localTimeMs = currentTimeMs + TimeUnit.HOURS.toMillis(schedule.timezoneOffset.toLong())
        val millisSinceDayStart = localTimeMs % TimeUnit.DAYS.toMillis(1)

        val videos = schedule.scheduledVideos.toMutableList()
        videos.sortBy { it.startTime }
        val currentVideo =
            videos.lastOrNull { it.startTime.toNanoOfDay() <= millisSinceDayStart * 1_000_000 }
                ?: videos.last()

        var millisSinceVideoStart =
            millisSinceDayStart - currentVideo.startTime.toNanoOfDay() / 1_000_000
        if (millisSinceVideoStart < 0)
            millisSinceVideoStart += TimeUnit.DAYS.toMillis(1)
        synchronizeVideo(currentVideo, millisSinceVideoStart.toInt())
    }

    private fun synchronizeVideo(video: ScheduledVideo, millisSinceVideoStart: Int) {
        if (syncVideoPlayer.busy) {
            return
        }

        if (syncVideoPlayer.preparedVideo != video) {
            syncVideoPlayer.loadVideo(video)
            return
        }

        val currentOffset = syncVideoPlayer.currentPosition - getSeekPosition(video, millisSinceVideoStart)

        if (synchronizedLastTime) {
            // Checking how well the last synchronization went.
            if (currentOffset.absoluteValue < 100) {
                /* When we begin the playback of a new video, the lag is really high,
                 * and we don't include that in the average. */
                averageLagMs += currentOffset.absoluteValue
                val oldSeekBias = seekBiasMs.value.toInt()
                seekBiasMs += (oldSeekBias + currentOffset)
            }
        }
        val needToSynchronize =
            currentOffset.absoluteValue > (averageLagMs.value * 2).coerceIn(30.0..50.0)
        synchronizedLastTime = needToSynchronize

        if (needToSynchronize) {
            Logger.log("The lag is too high, synchronizing the video!")
            // We introduce a delay so that the sync operation finishes in time.
            val seekDelay = (syncVideoPlayer.averageSyncDurationMs.value * 2).toInt()
            val positionAfterDelay =
                getSeekPosition(video, millisSinceVideoStart + seekDelay - seekBiasMs.value.toInt())
            syncVideoPlayer.seekWithDelay(positionAfterDelay, seekDelay)
        }
    }

    private fun getSeekPosition(video: ScheduledVideo, millisSinceVideoStart: Int): Int {
        var seekPosition = millisSinceVideoStart
        if (video.loop)
            seekPosition %= syncVideoPlayer.currentVideoDurationMs
        else
            seekPosition = seekPosition.coerceAtMost(syncVideoPlayer.currentVideoDurationMs)
        return seekPosition
    }

    /* Hack: since MediaPlayer.seekTo() is really slow, we call that method
     * on a MediaPlayer that's not visible (because it's in background).
     * Then, after it finishes, we swap the background and the foreground.
     * This class is not thread-safe, use it from UI thread only. */
    private inner class SyncVideoPlayer(
        private var foregroundTextureView: TextureView,
        private var backgroundTextureView: TextureView
    ) {
        @Volatile
        var busy: Boolean = false
            private set(value) {
                if (field && value) {
                    throw RuntimeException("Player is already busy!")
                }
                field = value
            }

        var preparedVideo: ScheduledVideo? = null

        var currentVideoDurationMs: Int = 0
            private set

        val averageSyncDurationMs = AverageInt(1000)

        val currentPosition: Int
            get() {
                return if (!soughtToEnd)
                    mediaPlayerByView[foregroundTextureView]!!.currentPosition
                else
                    currentVideoDurationMs
            }

        private var soughtToEnd: Boolean = false

        private val allTextureViews = listOf(foregroundTextureView, backgroundTextureView)

        private val mediaPlayerByView = mutableMapOf<TextureView, MediaPlayer>()

        private val preparedMediaPlayers = mutableSetOf<MediaPlayer>()

        private val mediaPlayersWithSurface = mutableSetOf<MediaPlayer>()

        private var hadPrepareErrors = false

        private var missingVideoLastTime: String? = null

        private val handler = Handler(Looper.getMainLooper())

        init {
            // Initializing the texture views with media players
            busy = true

            for (textureView in allTextureViews) {
                val mediaPlayer = MediaPlayer()
                mediaPlayerByView[textureView] = mediaPlayer

                textureView.surfaceTextureListener = object: TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        mediaPlayer.setSurface(Surface(surface))
                        mediaPlayersWithSurface.add(mediaPlayer)
                        updateBusyState()
                    }

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        mediaPlayer.setSurface(null)
                        mediaPlayersWithSurface.remove(mediaPlayer)
                        updateBusyState()
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

                    override fun onSurfaceTextureSizeChanged(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {}

                    fun updateBusyState() {
                        val shouldBeBusy = mediaPlayersWithSurface.size < 2
                        if (busy != shouldBeBusy)
                            busy = shouldBeBusy
                    }
                }
            }
        }

        fun loadVideo(video: ScheduledVideo) {
            val videoFile = fileManager.getFile(video.filename)

            if (videoFile == null) {
                if (missingVideoLastTime != video.filename) {
                    Logger.log("${video.filename} is missing! Waiting.")
                }
                missingVideoLastTime = video.filename
                stop()
                return
            } else {
                missingVideoLastTime = null
            }

            Logger.log("Loading ${video.filename}")

            busy = true
            preparedMediaPlayers.clear()
            hadPrepareErrors = false

            fun onPreparationEnded(mediaPlayer: MediaPlayer, success: Boolean) {
                preparedMediaPlayers.add(mediaPlayer)
                hadPrepareErrors = hadPrepareErrors or !success
                if (preparedMediaPlayers.size == 2) {  // All media players have prepared.
                    busy = false
                    if (!hadPrepareErrors) {
                        preparedVideo = video
                    }
                    for (otherMediaPlayer in mediaPlayerByView.values) {
                        otherMediaPlayer.setOnErrorListener { _, _, _ ->
                            Logger.log("Error during playback of ${video.filename}")
                            preparedVideo = null
                            true
                        }
                    }
                }
            }

            for ((textureView, mediaPlayer) in mediaPlayerByView) {
                mediaPlayer.setOnPreparedListener {
                    currentVideoDurationMs = mediaPlayer.duration
                    if (currentVideoDurationMs == -1) {
                        Logger.log("Cannot get ${video.filename} duration!")
                        onPreparationEnded(mediaPlayer, success = false)
                    }
                    adjustAspectRatio(textureView, mediaPlayer)
                    onPreparationEnded(mediaPlayer, success = true)
                }

                mediaPlayer.setOnErrorListener { _, _, _ ->
                    onPreparationEnded(mediaPlayer, success = false)
                    true
                }
            }

            for (mediaPlayer in mediaPlayerByView.values) {
                mediaPlayer.reset()
                mediaPlayer.setDataSource(activity, videoFile.uri)
                mediaPlayer.prepareAsync()
            }
        }

        private fun adjustAspectRatio(textureView: TextureView, mediaPlayer: MediaPlayer) {
            // Based on https://stackoverflow.com/a/12335916/6120487
            val videoWidth = mediaPlayer.videoWidth
            val videoHeight = mediaPlayer.videoHeight
            val videoProportion = videoWidth.toFloat() / videoHeight.toFloat()

            val (screenWidth, screenHeight) = getScreenSize()
            val screenProportion = screenWidth.toFloat() / screenHeight.toFloat()
            val params = textureView.layoutParams

            if (videoProportion > screenProportion) {
                params.width = screenWidth
                params.height = (screenWidth.toFloat() / videoProportion).toInt()
            } else {
                params.width = (videoProportion * screenHeight.toFloat()).toInt()
                params.height = screenHeight
            }
            textureView.layoutParams = params
        }

        private fun getScreenSize(): Pair<Int, Int> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val windowMetrics = activity.windowManager.currentWindowMetrics
                windowMetrics.bounds.width() to windowMetrics.bounds.height()
            } else {
                val displayMetrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                activity.windowManager.defaultDisplay.getMetrics(displayMetrics)
                displayMetrics.widthPixels to displayMetrics.heightPixels
            }
        }

        fun seekWithDelay(positionMs: Int, delayMs: Int) {
            busy = true
            val backgroundMPlayer = mediaPlayerByView[backgroundTextureView]!!
            val seekStartRealtime = SystemClock.elapsedRealtime()
            backgroundMPlayer.setOnSeekCompleteListener {
                val seekEndRealtime = SystemClock.elapsedRealtime()
                val elapsedMs = seekEndRealtime - seekStartRealtime
                averageSyncDurationMs += elapsedMs.toInt()
                val waitMs = (delayMs - elapsedMs).coerceAtLeast(0)
                handler.postDelayed({
                    swapForegroundAndBackground()
                    busy = false
                }, waitMs)
            }
            soughtToEnd = (positionMs == currentVideoDurationMs)
            val syncMode = if (!soughtToEnd) MediaPlayer.SEEK_CLOSEST else MediaPlayer.SEEK_PREVIOUS_SYNC
            val volume = if (!soughtToEnd) 1.0f else 0.0f
            backgroundMPlayer.seekTo(positionMs.toLong(), syncMode)
            backgroundMPlayer.setVolume(volume, volume)
        }

        fun swapForegroundAndBackground() {
            // Swapping the variables
            run {
                val tmp = foregroundTextureView
                foregroundTextureView = backgroundTextureView
                backgroundTextureView = tmp
            }
            foregroundTextureView.bringToFront()

            mediaPlayerByView[backgroundTextureView]!!.apply {
                if (isPlaying)
                    pause()
            }
            val foregroundMediaPlayer = mediaPlayerByView[foregroundTextureView]!!
            foregroundMediaPlayer.start()
            if (soughtToEnd)
                foregroundMediaPlayer.pause()
        }

        fun stop() {
            for (mediaPlayer in mediaPlayerByView.values) {
                mediaPlayer.reset()
            }
            preparedVideo = null
            soughtToEnd = false
            busy = false
        }
    }

    private class AverageInt(initialValue: Int, val oldCoefficient: Double = 0.9) {
        var value: Double = initialValue.toDouble()
            private set

        operator fun plusAssign(newValue: Int) {
            value = value * oldCoefficient + newValue * (1 - oldCoefficient)
        }
    }
}