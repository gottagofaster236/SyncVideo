package com.lr_soft.syncvideo

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager

class SettingsActivity : AppCompatActivity() {
    private lateinit var continueButton: Button
    private lateinit var preferences: SharedPreferences
    private lateinit var fileManager: FileManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        if (savedInstanceState == null) {
            supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.settings, SettingsFragment())
                    .commit()
        }
        continueButton = findViewById(R.id.continueButton)

        if (!needSetup()) {
            continueButton.visibility = View.GONE
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }

        fileManager = FileManager(this)
        fileManager.registerAppFolderActivityResult(this)
        preferences = PreferenceManager.getDefaultSharedPreferences(this)
    }

    fun onContinueButtonClick(view: View) {
        if (!checkSettings()) {
            return
        }
        preferences.edit().putBoolean(FINISHED_SETUP_KEY, true).apply()

        val intent = Intent(this, VideoActivity::class.java)
        startActivity(intent)
        finish()  // Prevent the back press
    }

    fun onSelectAppFolderClicked(view: View) {
        fileManager.selectAppFolder()
    }

    private fun checkSettings(): Boolean {
        val deviceIdKey = getString(R.string.pref_device_id_key)
        val deviceId = preferences.getString(deviceIdKey, null)
        if (deviceId.isNullOrEmpty()) {
            Toast.makeText(this, getString(R.string.device_id_should_not_be_empty), Toast.LENGTH_SHORT)
                .show()
            return false
        }
        return true
    }

    companion object {
        fun Context.needSetup(): Boolean {
            val preferences = PreferenceManager.getDefaultSharedPreferences(this)
            return !preferences.getBoolean(FINISHED_SETUP_KEY, false)
        }

        private const val FINISHED_SETUP_KEY = "finishedSetup"
    }

    // Make the back button work correctly.
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            super.onBackPressed()
        }
        return super.onOptionsItemSelected(item)
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            val preference = findPreference<EditTextPreference>(getString(R.string.pref_device_id_key))
            // Make the preference accept numeric input only.
            preference?.setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_NUMBER
            }
            // Checking that the input is not empty
            preference?.setOnPreferenceChangeListener { _, newValue ->
                (newValue as String).isNotEmpty()
            }
        }
    }
}