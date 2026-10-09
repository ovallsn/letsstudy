package com.oriol.letsstudy.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlin.math.min

class ProfilePhotoStore(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val photoDirectory = File(context.applicationContext.filesDir, "profile")

    fun save(uri: Uri): String {
        if (!photoDirectory.exists() && !photoDirectory.mkdirs()) error("The app could not save this photo.")
        val id = UUID.randomUUID().toString()
        val photoFile = File(photoDirectory, "profile-$id.jpg")
        val temporary = File(photoDirectory, "profile-$id.tmp")
        var source: Bitmap? = null
        var square: Bitmap? = null
        var thumbnail: Bitmap? = null
        try {
            val decoded = decode(uri).also { source = it }
            val side = min(decoded.width, decoded.height)
            require(side > 0) { "Choose a valid image file." }
            val crop = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side).also { square = it }
            val outputBitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).also { thumbnail = it }
            Canvas(outputBitmap).apply {
                drawColor(Color.WHITE)
                drawBitmap(crop, null, Rect(0, 0, 512, 512), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            }
            FileOutputStream(temporary).use { output ->
                check(outputBitmap.compress(Bitmap.CompressFormat.JPEG, 84, output)) { "This photo could not be saved." }
                output.fd.sync()
            }
            check(temporary.renameTo(photoFile)) { "This photo could not be saved." }
            photoDirectory.listFiles { file -> file.name.startsWith("profile-") && file.extension == "jpg" && file != photoFile }
                ?.forEach(File::delete)
            return photoFile.absolutePath
        } catch (error: Exception) {
            temporary.delete()
            photoFile.delete()
            throw error
        } finally {
            listOfNotNull(thumbnail, square, source).distinct().forEach { bitmap ->
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
    }

    fun delete() {
        photoDirectory.listFiles { file -> file.name.startsWith("profile-") }?.forEach(File::delete)
    }

    fun createLeaderboardThumbnail(path: String): ByteArray {
        val source = BitmapFactory.decodeFile(path) ?: throw IOException("The selected profile photo could not be reopened.")
        val thumbnail = Bitmap.createScaledBitmap(source, BOARD_SIZE, BOARD_SIZE, true)
        try {
            val output = ByteArrayOutputStream()
            for (quality in intArrayOf(76, 64, 52, 40)) {
                output.reset()
                if (!thumbnail.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
                    throw IOException("The selected profile photo could not be prepared for the community board.")
                }
                if (output.size() <= MAX_BOARD_THUMBNAIL_BYTES) return output.toByteArray()
            }
            throw IOException("The selected profile photo could not be reduced for the community board.")
        } finally {
            if (thumbnail !== source) thumbnail.recycle()
            source.recycle()
        }
    }

    private fun decode(uri: Uri): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val imageSource = ImageDecoder.createSource(resolver, uri)
            return ImageDecoder.decodeBitmap(imageSource) { decoder, info, _ ->
                require(info.size.width > 0 && info.size.height > 0) { "Choose a valid image file." }
                var sampleSize = 1
                while (maxOf(info.size.width, info.size.height) / sampleSize > 1024) sampleSize *= 2
                decoder.setTargetSampleSize(sampleSize)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
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
        val decoded = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        } ?: error("This photo could not be opened.")
        return try {
            transform(decoded, orientation).also { transformed -> if (transformed !== decoded) decoded.recycle() }
        } catch (error: Exception) {
            decoded.recycle()
            throw error
        }
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

    companion object {
        const val BOARD_SIZE = 96
        const val MAX_BOARD_THUMBNAIL_BYTES = 12 * 1024
    }
}
