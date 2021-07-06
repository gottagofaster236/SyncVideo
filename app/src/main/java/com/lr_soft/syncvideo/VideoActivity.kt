package com.lr_soft.syncvideo

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.TextureView
import android.view.View
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.lr_soft.syncvideo.SettingsActivity.Companion.needSetup

class VideoActivity : AppCompatActivity(), Logger.LogListener {
    private lateinit var clientServerSelector: ClientServerSelector
    private lateinit var videoSynchronizer: VideoSynchronizer
    private lateinit var videoOverlay: View
    private var animationDuration: Int = 0
    private lateinit var logTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Gotta to initialize clientServerSelector before the next if
        clientServerSelector = ClientServerSelector(applicationContext)

        if (needSetup()) {
            goToSettings()
            return
        }

        setContentView(R.layout.activity_video)
        videoOverlay = findViewById(R.id.video_overlay)
        animationDuration = resources.getInteger(android.R.integer.config_mediumAnimTime)
        logTextView = findViewById(R.id.log_text_view)
        val foregroundVideoView = findViewById<TextureView>(R.id.foreground_video_view)
        val backgroundVideoView = findViewById<TextureView>(R.id.background_video_view)

        videoSynchronizer = VideoSynchronizer(
            activity = this,
            clientServerSelector,
            foregroundVideoView,
            backgroundVideoView
        )
    }

    private fun goToSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
        if (needSetup()) {
            // Make sure the user cannot return to this activity before filling in the settings.
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        setFullscreen()
        Logger.registerLogListener(this)
        onNewLogMessage()
        clientServerSelector.updateSelection()
        videoSynchronizer.start()
    }

    override fun onPause() {
        Logger.unregisterLogListener(this)
        super.onPause()
    }

    override fun onDestroy() {
        videoSynchronizer.stop()
        clientServerSelector.stop()
        super.onDestroy()
    }

    fun onSettingsButtonClick(view: View) {
        toggleVideoOverlayVisibility()
        goToSettings()
    }

    fun toggleVideoOverlayVisibility(view: View? = null) {
        videoOverlay.apply {
            if (visibility == View.GONE) {
                alpha = 0f
                visibility = View.VISIBLE
                animate()
                    .alpha(1f)
                    .setDuration(animationDuration.toLong())
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            alpha = 1f
                        }
                    })
            } else {
                animate()
                    .alpha(0f)
                    .setDuration(animationDuration.toLong())
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            visibility = View.GONE
                        }
                    })
            }
        }
    }

    override fun onNewLogMessage() {
        runOnUiThread { logTextView.text = Logger.getLogs().joinToString("\n") }
    }

    private fun setFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}