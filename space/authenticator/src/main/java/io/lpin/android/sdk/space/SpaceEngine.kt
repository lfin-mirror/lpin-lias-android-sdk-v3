package io.lpin.android.sdk.space

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.torchvision.TensorImageUtils
import java.io.File

class SpaceEngine(val context: Context) {
    private lateinit var module: Module

    fun init() {
        module = getModule()
    }

    private fun getModule(): Module {
        context.applicationContext.copyAssets("model_android.pt")
        val modulePath = "${context.applicationContext.getExternalFilesDir(null)}/model_android.pt"
        return Module.load(modulePath)
    }

    fun getFeature(image: Bitmap): FloatArray {
        val input = TensorImageUtils.bitmapToFloat32Tensor(image, MEAN_RGB, STD_RGB)
        val output = module.forward(IValue.from(input)).toTensor()
        return output.dataAsFloatArray
    }

    fun destroy() {
        try {
            module.destroy()
        } catch (ignore: Exception) {
        }
    }


    private fun Context.copyAssets(path: String) {
        this.assets.list(path).tryCatch {
            if (it!!.isEmpty()) {
                copyFile(path)
                return
            }

            File("${this.getExternalFilesDir(null)}/$path").mkdirs()
            it.forEach {
                val dirPath = if (path == "") "" else "$path/"
                copyAssets("$dirPath$it")
            }
        }
    }

    private fun Context.copyFile(filename: String) {
        this.assets.open(filename).use { stream ->
            File("${this.getExternalFilesDir(null)}/$filename").outputStream().use { stream.copyTo(it) }
        }
    }

    private inline fun <T, R> T.tryCatch(block: (T) -> R): R {
        try {
            return block(this)
        } catch (e: Exception) {
            Log.e("TAG", "I/O Exception", e)
            throw e
        }
    }

    companion object {
        private val MEAN_RGB = floatArrayOf(0.5F, 0.5F, 0.5F)
        private val STD_RGB = floatArrayOf(0.5F, 0.5F, 0.5F)
    }
}