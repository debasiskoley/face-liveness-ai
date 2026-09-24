package com.example.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.example.model.BiometricVector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Loads and saves enrolled profile pictures, then extracts an identity template
 * from the photo (ArcFace ONNX when bundled).
 */
object ProfileBiometricHelper {
    private const val TAG = "ProfileBiometricHelper"

    suspend fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val rawBmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }.copy(Bitmap.Config.ARGB_8888, true)

            val matrix = Matrix().apply { postRotate(0f) }
            val rotatedBmp = Bitmap.createBitmap(rawBmp, 0, 0, rawBmp.width, rawBmp.height, matrix, true)
            if (rawBmp != rotatedBmp && !rawBmp.isRecycled) {
                rawBmp.recycle()
            }
            rotatedBmp
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap from uri $uri", e)
            null
        }
    }

    suspend fun saveBitmapLocally(context: Context, bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "enrolled_faces")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "face_${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        Uri.fromFile(file).toString()
    }

    suspend fun extractIdentityTemplate(context: Context, photoUri: String?): BiometricVector {
        if (photoUri.isNullOrBlank()) return BiometricVector.EMPTY
        val uri = Uri.parse(photoUri)
        val bitmap = loadBitmapFromUri(context, uri) ?: return BiometricVector.EMPTY
        val vector = ArcFaceEmbedder.getInstance(context).embedFromBitmap(bitmap)
        Log.i(TAG, "Enroll template uri=$photoUri dim=${vector?.features?.size ?: 0}")
        return if (vector != null && BiometricVector.isIdentityTemplate(vector)) {
            vector
        } else {
            Log.w(TAG, "No ArcFace template from photo; enroll will not match Office In")
            BiometricVector.EMPTY
        }
    }
}
