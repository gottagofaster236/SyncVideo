package com.lr_soft.syncvideo

import android.app.Activity
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import android.view.TextureView
import androidx.preference.PreferenceManager
import com.lyft.kronos.AndroidClockFactory
import com.lyft.kronos.KronosClock
import java.util.*
import java.util.concurrent.TimeUnit

class VideoSynchronizer(
    private val activity: Activity,
    private val clientOrServerSelector: ClientServerSelector,
    foregroundVideoView: TextureView,
    backgroundVideoView: TextureView
) {
    private val syncVideoPlayer = SyncVideoPlayer(foregroundVideoView, backgroundVideoView)
    private lateinit var kronosClock: KronosClock
    private val fileManager = FileManager(activity.applicationContext)
    private val preferences = PreferenceManager.getDefaultSharedPreferences(activity)
    private val timer = Timer()
    private var isRunning = false

    fun start() {
        isRunning = true
        kronosClock = AndroidClockFactory.createKronosClock(activity.applicationContext)
        kronosClock.syncInBackground()
        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                synchronizeVideo()
            }
        }, 0, 1000)
    }

    fun stop() {
        isRunning = false
        kronosClock.shutdown()
        timer.cancel()
        syncVideoPlayer.preparedVideo = null
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
        val currentTimeMs = kronosClock.getCurrentTimeMs()
        val localTimeMs = currentTimeMs + TimeUnit.HOURS.toMillis(schedule.timezoneOffset.toLong())
        val millisSinceDayStart = localTimeMs % TimeUnit.DAYS.toMillis(1)

        val videos = schedule.scheduledVideos.toMutableList()
        videos.sortBy { it.startTime }
        val currentVideo =
            videos.firstOrNull { it.startTime.toNanoOfDay() <= millisSinceDayStart * 1000 }
                ?: videos.last()

        var millisSinceVideoStart =
            millisSinceDayStart - currentVideo.startTime.toNanoOfDay() / 1000
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

        val delay = (syncVideoPlayer.averageSyncDurationMs * 2).toInt()
        var seekPosition = millisSinceVideoStart + delay
        if (video.loop)
            seekPosition %= syncVideoPlayer.currentVideoDurationMs
        else
            seekPosition = seekPosition.coerceAtMost(syncVideoPlayer.currentVideoDurationMs)
        Logger.log("Seeking to $seekPosition after $delay")
        syncVideoPlayer.seekWithDelay(seekPosition, delay)
    }

    // This class is not thread-safe, use it from UI thread only.
    private inner class SyncVideoPlayer(
        private var foregroundVideoView: TextureView,
        private var backgroundVideoView: TextureView
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

        var averageSyncDurationMs: Double = 250.0
            private set

        private val allVideoViews = listOf(foregroundVideoView, backgroundVideoView)

        private val mediaPlayerByView = mapOf(foregroundVideoView to MediaPlayer(),
            backgroundVideoView to MediaPlayer())

        private val preparedMediaPlayers = mutableSetOf<MediaPlayer>()

        private val mediaPlayersWithSurface = mutableSetOf<MediaPlayer>()

        private var hadPrepareErrors = false

        private val handler = Handler(Looper.getMainLooper())

        init {
            // Initializing the texture views with media players
            busy = true

            for (videoView in allVideoViews) {
                videoView.surfaceTextureListener = object: TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        val mediaPlayer = mediaPlayerByView[videoView]!!
                        mediaPlayer.setSurface(Surface(surface))
                        mediaPlayersWithSurface.add(mediaPlayer)
                        updateBusyState()
                    }

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        val mediaPlayer = mediaPlayerByView[videoView]!!
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
                ?: return Logger.log("Waiting for ${video.filename} to be downloaded")

            busy = true
            preparedMediaPlayers.clear()

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

            for (mediaPlayer in mediaPlayerByView.values) {
                mediaPlayer.setOnPreparedListener { _ ->
                    currentVideoDurationMs = mediaPlayer.duration
                    if (currentVideoDurationMs == -1) {
                        Logger.log("Cannot get ${video.filename} duration!")
                        onPreparationEnded(mediaPlayer, success = false)
                    }
                    mediaPlayer.isLooping = video.loop
                    onPreparationEnded(mediaPlayer, success = true)
                }

                mediaPlayer.setOnErrorListener { _, _, _ ->
                    onPreparationEnded(mediaPlayer, success = false)
                    true
                }
            }

            for (mediaPlayer in mediaPlayerByView.values) {
                mediaPlayer.setDataSource(activity, videoFile.uri)
            }
        }

        fun seekWithDelay(positionMs: Int, delayMs: Int) {
            busy = true
            val backgroundMPlayer = mediaPlayerByView[backgroundVideoView]!!
            val seekStartRealtime = SystemClock.elapsedRealtime()
            backgroundMPlayer.setOnSeekCompleteListener {
                val seekEndRealtime = SystemClock.elapsedRealtime()
                val elapsedMs = seekEndRealtime - seekStartRealtime
                updateAverageSeekDuration(elapsedMs.toInt())
                val waitMs = (delayMs - elapsedMs).coerceAtLeast(0)
                handler.postDelayed({
                    swapForegroundAndBackground()
                    busy = false
                }, waitMs)
            }
            backgroundMPlayer.seekTo(positionMs.toLong(), MediaPlayer.SEEK_CLOSEST)
        }

        fun updateAverageSeekDuration(seekDurationMs: Int) {
            val oldCoefficient = 0.7
            averageSyncDurationMs = averageSyncDurationMs * oldCoefficient +
                    seekDurationMs * (1 - oldCoefficient)
        }

        fun swapForegroundAndBackground() {
            // Swapping the variables
            run {
                val tmp = foregroundVideoView
                foregroundVideoView = backgroundVideoView
                backgroundVideoView = tmp
            }
            foregroundVideoView.bringToFront()

            mediaPlayerByView[backgroundVideoView]!!.pause()
            mediaPlayerByView[foregroundVideoView]!!.start()
        }
    }
}