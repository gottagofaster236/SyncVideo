package com.lr_soft.syncvideo

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class VideoActivity : AppCompatActivity() {
    private lateinit var videoOverlay: View
    private var animationDuration: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video)
        videoOverlay = findViewById(R.id.video_overlay)
        animationDuration = resources.getInteger(android.R.integer.config_mediumAnimTime)
        setFullscreen()
    }

    fun onSettingsButtonClick(view: View) {
        toggleVideoOverlayVisibility()
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
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
            }
            else {
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

    private fun setFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}