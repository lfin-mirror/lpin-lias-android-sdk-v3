package io.lpin.android.sdk.face.extenstions

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Path
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

fun String.toBitmap(): Bitmap? {
    return try {
        Base64.decode(replace("%2B", "+"), Base64.NO_WRAP).let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    } catch (e: Exception) {
        null
    }
}

fun Bitmap.toJpegByteArray(quality: Int = 85): ByteArray? {
    return try {
        val normalizedQuality = quality.coerceIn(0, 100)
        val output = ByteArrayOutputStream()
        val compressed = compress(Bitmap.CompressFormat.JPEG, normalizedQuality, output)
        if (compressed) output.toByteArray() else null
    } catch (e: Exception) {
        null
    }
}

fun Bitmap.cache(path: String, name: String): File {
    val f = File(path, name)
    FileOutputStream(f).apply {
        this@cache.compress(Bitmap.CompressFormat.JPEG, 100, this)
        this.close()
    }
    return f
}
