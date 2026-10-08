package com.oriol.letsstudy.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

class ProfilePhotoStore(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val photoDirectory = File(context.applicationContext.filesDir, "profile")

    fun save(uri: Uri): String {
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: error("This photo could not be opened.")
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Choose a valid image file." }
        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > 1024) sampleSize *= 2
        val source = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        } ?: error("This photo could not be opened.")
        val transformed = transform(source, orientation)
        val side = min(transformed.width, transformed.height)
        val square = Bitmap.createBitmap(transformed, (transformed.width - side) / 2, (transformed.height - side) / 2, side, side)
        val thumbnail = Bitmap.createScaledBitmap(square, 512, 512, true)
        if (!photoDirectory.exists() && !photoDirectory.mkdirs()) error("The app could not save this photo.")
        val photoFile = File(photoDirectory, "profile-${System.currentTimeMillis()}.jpg")
        val temporary = File(photoDirectory, "profile-photo.tmp")
        FileOutputStream(temporary).use { output ->
            check(thumbnail.compress(Bitmap.CompressFormat.JPEG, 84, output)) { "This photo could not be saved." }
            output.fd.sync()
        }
        check(temporary.renameTo(photoFile)) { "This photo could not be saved." }
        photoDirectory.listFiles { file -> file.name.startsWith("profile-") && file.extension == "jpg" && file != photoFile }
            ?.forEach(File::delete)
        if (thumbnail !== square) thumbnail.recycle()
        if (square !== transformed && square !== source) square.recycle()
        if (transformed !== source) transformed.recycle()
        source.recycle()
        return photoFile.absolutePath
    }

    fun delete() {
        photoDirectory.listFiles { file -> file.name.startsWith("profile-") }?.forEach(File::delete)
    }

    private fun transform(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(270f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
        }
        return if (matrix.isIdentity) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
