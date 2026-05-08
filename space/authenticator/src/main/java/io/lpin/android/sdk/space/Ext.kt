package io.lpin.android.sdk.space

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import kotlin.math.min


/** Bitmap Extensions **/

fun Bitmap.rotate(value: Float): Bitmap = Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { setRotate(value) }, false)
fun Bitmap.resize(w: Int, h: Int): Bitmap = Bitmap.createScaledBitmap(this, w, h, false)
fun Bitmap.base64Encoding(): String {
    val output = ByteArrayOutputStream()
    this.compress(Bitmap.CompressFormat.JPEG, 100, output)
    return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
}
private inline fun <T, R> T.tryOrNull(block: (T) -> R): R? {
    return try {
        block(this)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}