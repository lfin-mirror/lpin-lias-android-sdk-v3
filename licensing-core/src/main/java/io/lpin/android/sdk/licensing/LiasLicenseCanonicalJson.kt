package io.lpin.android.sdk.licensing

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.math.BigDecimal

internal object LiasLicenseCanonicalJson {
    fun canonicalize(value: Any?): String {
        return when (value) {
            null, JsonNull.INSTANCE -> "null"
            is JsonObject -> canonicalizeJsonObject(value)
            is JsonArray -> canonicalizeJsonArray(value)
            is JsonPrimitive -> canonicalizeJsonPrimitive(value)
            is Map<*, *> -> canonicalizeMap(value)
            is Iterable<*> -> canonicalizeIterable(value)
            is Array<*> -> canonicalizeIterable(value.asIterable())
            is String -> quoteString(value)
            is Boolean -> value.toString()
            is Number -> canonicalizeNumber(value)
            else -> quoteString(value.toString())
        }
    }

    private fun canonicalizeJsonObject(value: JsonObject): String {
        val names = value.keySet().sorted()
        return buildString {
            append('{')
            names.forEachIndexed { index, key ->
                if (index > 0) append(',')
                append(quoteString(key))
                append(':')
                append(canonicalize(value.get(key)))
            }
            append('}')
        }
    }

    private fun canonicalizeJsonArray(value: JsonArray): String {
        return buildString {
            append('[')
            value.forEachIndexed { index, element ->
                if (index > 0) append(',')
                append(canonicalize(element))
            }
            append(']')
        }
    }

    private fun canonicalizeJsonPrimitive(value: JsonPrimitive): String {
        return when {
            value.isString -> quoteString(value.asString)
            value.isBoolean -> value.asBoolean.toString()
            value.isNumber -> canonicalizeNumber(value.asNumber)
            else -> quoteString(value.toString())
        }
    }

    private fun canonicalizeMap(value: Map<*, *>): String {
        val names = value.keys.map { it.toString() }.sorted()
        return buildString {
            append('{')
            names.forEachIndexed { index, key ->
                if (index > 0) append(',')
                append(quoteString(key))
                append(':')
                append(canonicalize(value[key]))
            }
            append('}')
        }
    }

    private fun canonicalizeIterable(value: Iterable<*>): String {
        return buildString {
            append('[')
            value.forEachIndexed { index, item ->
                if (index > 0) append(',')
                append(canonicalize(item))
            }
            append(']')
        }
    }

    private fun quoteString(value: String): String {
        val builder = StringBuilder(value.length + 2)
        builder.append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> builder.append("\\\\")
                '"' -> builder.append("\\\"")
                '\b' -> builder.append("\\b")
                '\u000C' -> builder.append("\\f")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                else -> {
                    if (character.code < 0x20) {
                        builder.append("\\u")
                        builder.append(character.code.toString(16).padStart(4, '0'))
                    } else {
                        builder.append(character)
                    }
                }
            }
        }
        builder.append('"')
        return builder.toString()
    }

    private fun canonicalizeNumber(value: Number): String {
        return when (value) {
            is Byte, is Short, is Int, is Long -> value.toString()
            is Float, is Double -> {
                val doubleValue = value.toDouble()
                require(doubleValue.isFinite()) { "License JSON contains a non-finite number" }
                BigDecimal.valueOf(doubleValue).stripTrailingZeros().toPlainString()
            }
            else -> BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
        }
    }
}

private inline fun <T> Iterable<T>.forEachIndexed(action: (Int, T) -> Unit) {
    var index = 0
    for (item in this) {
        action(index++, item)
    }
}
