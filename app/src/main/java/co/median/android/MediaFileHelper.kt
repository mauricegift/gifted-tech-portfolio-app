package co.median.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object MediaFileHelper {
    @JvmStatic
    fun resizeJpgUriTo480p(
        context: Context, imageUri: Uri, callback: FileCallback?
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val contentResolver = context.contentResolver
                val originalBitmap: Bitmap? = contentResolver.openInputStream(imageUri)?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream)
                }

                if (originalBitmap == null) {
                    withContext(Dispatchers.Main) { callback?.onFailure(Exception("Failed to decode image")) }
                    return@launch
                }

                val width = originalBitmap.width
                val height = originalBitmap.height
                val isPortrait = height > width
                val shortSide = if (isPortrait) width else height
                val longSide = if (isPortrait) height else width
                val scaleFactor = 480f / shortSide
                val newShortSide = 480
                val newLongSide = (longSide * scaleFactor).toInt()
                val targetWidth = if (isPortrait) newShortSide else newLongSide
                val targetHeight = if (isPortrait) newLongSide else newShortSide

                // Resize bitmap
                val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, true)

                // Clean up original bitmap to free memory
                if (!originalBitmap.isRecycled) {
                    originalBitmap.recycle()
                }

                // Create output file
                val downloadsCacheDir = File(context.cacheDir, "downloads")
                if (!downloadsCacheDir.exists())
                    downloadsCacheDir.mkdirs()

                // Should only have one instance to save memory
                val outputFile = File(downloadsCacheDir, "temp_capture_image.jpg")

                // Save resized image
                FileOutputStream(outputFile).use { outputStream ->
                    resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                }

                withContext(Dispatchers.Main) {
                    callback?.onSuccess(fileToUri(context, outputFile))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback?.onFailure(e)
                }
            }
        }
    }

    @JvmStatic
    fun moveVideoToCache(context: Context, file: Uri,  callback: FileCallback?) {
        CoroutineScope(Dispatchers.IO).launch {
            val contentResolver = context.contentResolver

            // Create output file
            val downloadsCacheDir = File(context.cacheDir, "downloads")
            if (!downloadsCacheDir.exists())
                downloadsCacheDir.mkdirs()

            val destinationFile = File(downloadsCacheDir, "temp_video_recording.mp4")

            try {
                contentResolver.openInputStream(file)?.use { inputStream ->
                    FileOutputStream(destinationFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                // Delete original file
                val originalFile = File(file.path ?: "")
                if (originalFile.exists()) {
                    originalFile.delete()
                }

                withContext(Dispatchers.Main) {
                    callback?.onSuccess(fileToUri(context, destinationFile))
                }
            } catch (ex: Exception) {
                withContext(Dispatchers.Main) {
                    callback?.onFailure(ex)
                }
            }
        }
    }

    @JvmStatic
    fun fileToUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.applicationContext.packageName}.fileprovider",
            file
        )
    }
}

interface FileCallback {
    fun onSuccess(uri: Uri)
    fun onFailure(exception: Exception)
}
