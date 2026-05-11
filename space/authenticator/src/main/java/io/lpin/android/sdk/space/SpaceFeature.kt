package io.lpin.android.sdk.space

import android.util.Base64
import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.CborDecoder
import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.model.*
import co.nstant.`in`.cbor.model.Array
import co.nstant.`in`.cbor.model.Map
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

private const val TAG: String = "SpaceFeature"
private const val SPACE = "space"
private const val OBJECT = "object"
private const val DEVICE = "device"
private const val OBJECT_KEY_LABEL = "label"
private const val OBJECT_KEY_CONFIDENCE = "confidence"

data class SpaceFeature(
    var space: List<List<Float>>,
    var `object`: List<Pair<String, Float>>,
    var device: String = ""
)

fun spaceFeatureHASH(feature: FloatArray): String {
    val featureByteArray = ByteArrayOutputStream()
    val ds = DataOutputStream(featureByteArray)
    for (f in feature)
        ds.writeFloat(f)

    return SHA512().hash(featureByteArray.toByteArray())
}

fun SpaceFeature.featureEncoded(): String? {
    return featureEncoded(this.space, this.`object`, this.device)
}

// 공간이미지 특징점 데이터 → Base64(CborEncoded)
fun featureEncoded(
    space: List<List<Float>>,
    `object`: List<Pair<String, Float>>,
    device: String
): String? {
    try {
        val featureCborEncoded = ByteArrayOutputStream()
        CborEncoder(featureCborEncoded).encode(CborBuilder().apply {
            addMap().apply {
                this.putArray(SPACE).apply {
                    space.forEach { featureData ->
                        this.addArray().let { addArrayBuilder ->
                            featureData.forEach { addArrayBuilder.add(it) }
                        }
                    }
                }
                this.putArray(OBJECT).apply {
                    `object`.map {
                        addMap().put(OBJECT_KEY_LABEL, it.first)
                            .put(OBJECT_KEY_CONFIDENCE, it.second)
                    }
                }
                this.put(DEVICE, device)
            }
        }.build())

        return encodeBase64(featureCborEncoded.toByteArray())
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

private fun encodeBase64(value: ByteArray): String {
    return try {
        val encoderClass = Class.forName("java.util.Base64")
        val getEncoder = encoderClass.getMethod("getEncoder")
        val encoder = getEncoder.invoke(null)
        val encodeToString = encoder.javaClass.getMethod("encodeToString", ByteArray::class.java)
        encodeToString.invoke(encoder, value) as String
    } catch (ignore: Exception) {
        Base64.encodeToString(value, Base64.NO_WRAP)
    }
}

private fun decodeBase64(value: String): ByteArray {
    return try {
        val decoderClass = Class.forName("java.util.Base64")
        val getDecoder = decoderClass.getMethod("getDecoder")
        val decoder = getDecoder.invoke(null)
        val decode = decoder.javaClass.getMethod("decode", String::class.java)
        decode.invoke(decoder, value) as ByteArray
    } catch (ignore: Exception) {
        Base64.decode(value, Base64.NO_WRAP)
    }
}

fun featureDecoded(featureOriginal: String): SpaceFeature? {
    val feature = try {
        featureOriginal.replace("%2B", "+")
    } catch (ignore: Exception) {
        return null
    }

    val featureCborEncoded = decodeBase64(feature)
    val items = CborDecoder(ByteArrayInputStream(featureCborEncoded)).decode()
    val featureMap = items[0] as Map

    lateinit var space: List<List<Float>>
    lateinit var `object`: List<Pair<String, Float>>
    lateinit var device: String

    for (key in featureMap.keys) {
        when (key.toString()) {
            SPACE -> {
                val data = featureMap[key] as Array
                data.dataItems
                    .map { item -> item as Array }
                    .map { item ->
                        item.dataItems.map {
                            if (it is SinglePrecisionFloat)
                                it
                            else
                                it as HalfPrecisionFloat
                        }
                    }
                    .map { item -> item.map { it.value } }
                    .apply { space = this }
            }
            OBJECT -> {
                val data = featureMap[key] as Array
                data.dataItems
                    .map { item -> item as Map }
                    .map { item ->
                        var objectLabel = ""
                        var objectConfidence = 0.0f

                        item.keys.forEach { key ->
                            when (key.toString()) {
                                OBJECT_KEY_LABEL -> {
                                    objectLabel = (item[key] as UnicodeString).string
                                }
                                OBJECT_KEY_CONFIDENCE -> {
                                    objectConfidence = if (item[key] is SinglePrecisionFloat) {
                                        (item[key] as SinglePrecisionFloat).value
                                    } else {
                                        (item[key] as HalfPrecisionFloat).value
                                    }
                                }
                            }
                        }

                        Pair(objectLabel, objectConfidence)
                    }
                    .apply { `object` = this }
            }
            DEVICE -> {
                val data = featureMap[key] as UnicodeString
                device = data.string
            }
        }
    }
    return SpaceFeature(space, `object`, device)
}