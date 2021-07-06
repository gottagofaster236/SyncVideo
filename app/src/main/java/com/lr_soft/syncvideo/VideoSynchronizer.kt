package com.lr_soft.syncvideo

import android.app.Activity
import android.media.MediaPlayer
import android.widget.VideoView
import androidx.preference.PreferenceManager
import com.lyft.kronos.AndroidClockFactory
import com.lyft.kronos.KronosClock
import java.util.*
import java.util.concurrent.TimeUnit

class VideoSynchronizer(private val activity: Activity,
                        private val videoView: VideoView,
                        private val clientOrServerSelector  : ClientServerSelector) {
    private lateinit var kronosClock: KronosClock
    private val fileManager = FileManager(activity.applicationContext)
    private val preferences = PreferenceManager.getDefaultSharedPreferences(activity)
    private val timer = Timer()
    private var isRunning = false
    private var currentVideoPlaying: ScheduledVideo? = null
    private var currentVideoDurationMs: Int = 0

    fun start() {
        isRunning = true
        AndroidClockFactory.createKronosClock(activity.applicationContext)
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
        currentVideoPlaying = null  // VideoView doesn't restore the video after going to background
    }

    private fun synchronizeVideo() {
        val clientOrServer = clientOrServerSelector.clientOrServer?: return
        var schedule = clientOrServer.schedule ?: return  // Potentially expensive line
        val deviceIdKey = activity.getString(R.string.pref_device_id_key)
        val deviceId = preferences.getString(deviceIdKey, null) ?: return Logger.log("Empty deviceId!")
        schedule = schedule.filterByDeviceId(deviceId)
        checkVideosPresence(schedule, clientOrServer)
        activity.runOnUiThread { synchronizeVideoUiThread(schedule) }
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
        val currentVideo = videos.firstOrNull { it.startTime.toNanoOfDay() <= millisSinceDayStart * 1000 }
            ?: videos.last()

        var millisSinceVideoStart = currentVideo.startTime.toNanoOfDay() / 1000 - millisSinceDayStart
        if (millisSinceVideoStart < 0)
            millisSinceVideoStart += TimeUnit.DAYS.toMillis(1)
        synchronizeVideo(currentVideo, millisSinceVideoStart.toInt())
    }

    private fun synchronizeVideo(video: ScheduledVideo, millisSinceVideoStart: Int) {
        if (currentVideoPlaying != video) {
            val videoFile = fileManager.getFile(video.filename)
                ?: return Logger.log("Waiting for ${video.filename} to be downloaded")
            currentVideoPlaying = video
            currentVideoDurationMs = -1

            videoView.setOnPreparedListener { mp ->
                currentVideoDurationMs = mp.duration
                mp.isLooping = video.loop
            }

            videoView.setOnErrorListener { _, _, _ ->
                Logger.log("Error during video play.")
                currentVideoPlaying = null
                true
            }
            videoView.setVideoURI(videoFile.uri)
        } else {
            val seekPosition =
                if (video.loop)
                    millisSinceVideoStart % currentVideoDurationMs
                else
                    millisSinceVideoStart.coerceAtMost(currentVideoDurationMs)
            videoView.seekTo(seekPosition)  // ???
        }
    }

    private fun checkVideosPresence(schedule: Schedule, clientOrServer: ClientOrServer) {
        val videoFilenames = schedule.scheduledVideos.map { it.filename }.toSet()
        for (filename in videoFilenames) {
            if (fileManager.getFile(filename) == null) {
                clientOrServer.handleMissingFile(filename)
            }
        }
    }
}