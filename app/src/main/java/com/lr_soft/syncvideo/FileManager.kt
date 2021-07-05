package com.lr_soft.syncvideo

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager

class FileManager(private val context: Context) {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private lateinit var resultLauncher: ActivityResultLauncher<Intent>

    val folder: DocumentFile
        get() {
            val uriString = preferences.getString(FOLDER_URI_KEY, null)
            if (uriString != null) {
                val uri = Uri.parse(uriString)
                return DocumentFile.fromTreeUri(context, uri)!!
            } else {
                val filesDir = context.getExternalFilesDir(null) ?: context.filesDir
                return DocumentFile.fromFile(filesDir)
            }
        }

    fun registerAppFolderActivityResult(activity: AppCompatActivity) {
        resultLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(), ::onActivityResult)
    }

    fun selectAppFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        resultLauncher.launch(intent)
    }

    private fun onActivityResult(result: ActivityResult) {
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return

            val contentResolver = context.contentResolver
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, takeFlags)

            preferences.edit().apply {
                putBoolean(HAS_SELECTED_FOLDER_KEY, true)
                putString(FOLDER_URI_KEY, uri.toString())
            }.apply()
        }
    }

    fun getFile(filename: String): DocumentFile? {
        val file = folder.findFile(filename) ?: return null
        return if (file.exists()) file else null
    }

    private companion object {
        const val HAS_SELECTED_FOLDER_KEY = "hasSelectedFolder"
        const val FOLDER_URI_KEY = "folderUri"
    }
}