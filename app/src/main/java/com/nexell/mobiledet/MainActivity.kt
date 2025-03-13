package com.nexell.mobiledet

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.media.MediaScannerConnection
import java.io.InputStream
import android.util.Log
import com.nexell.mobiledet.databinding.ActivityMainBinding



class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        copyAssetsToGallery(applicationContext)

    }
    private fun copyAssetsToGallery(context: Context) {
        val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("assets_copied", false)) return

        context.assets.list("media")?.forEach { filename ->
            try {
                context.assets.open("media/$filename").use { input ->
                    val mimeType = when {
                        filename.endsWith(".jpg", true) -> "image/jpeg"
                        filename.endsWith(".jpeg", true) -> "image/jpeg"
                        filename.endsWith(".png", true) -> "image/png"
                        filename.endsWith(".mp4", true) -> "video/mp4"
                        else -> return@forEach
                    }
                    saveFileToGallery(context, input, filename, mimeType)
                }
            } catch (e: Exception) {
                Log.e("FileCopyError", "Error opening asset: $filename", e)
            }
        }

        prefs.edit().putBoolean("assets_copied", true).apply()
    }

    private fun saveFileToGallery(context: Context, input: InputStream, filename: String, mimeType: String) {
        val collection = if (mimeType.startsWith("video")) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/MyApp")
        }

        try {
            context.contentResolver.insert(collection, values)?.let { uri ->
                Log.d("FileCopy", "Inserting file to URI: $uri")

                context.contentResolver.openOutputStream(uri)?.use { output ->
                    Log.d("FileCopy", "Copying file: $filename")
                    input.copyTo(output)
                    Log.d("FileCopy", "File copied: $filename")

                    // 갤러리 새로 고침 (MediaScannerConnection 사용)
                    MediaScannerConnection.scanFile(context, arrayOf(uri.path), null) { path, scannedUri ->
                        Log.d("FileScan", "Scanned $path:$scannedUri")
                    }
                }
            } ?: run {
                Log.e("FileCopyError", "Failed to insert file into MediaStore: $filename")
            }
        } catch (e: Exception) {
            Log.e("FileCopyError", "Error copying file: $filename", e)
        }
    }
    }