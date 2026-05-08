package io.lpin.android.sdk.face.extenstions

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Path
import android.util.Base64
import java.io.File
import java.io.FileOutputStream

fun String.toBitmap(): Bitmap? {
    return try {
        Base64.decode(replace("%2B", "+"), Base64.NO_WRAP).let { BitmapFactory.decodeByteArray(it, 0, it.size) }
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
