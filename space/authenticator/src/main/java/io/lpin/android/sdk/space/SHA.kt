package io.lpin.android.sdk.space

import java.security.MessageDigest
import kotlin.experimental.and

class SHA512 : SHA {
    override fun hash(value: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-512").digest(value)
        val sb = StringBuilder()
        for (i in digest.indices) {
            sb.append(String.format("%02X", digest[i]))
        }
        return sb.toString().toUpperCase()
    }
}

interface SHA {
    fun hash(value: ByteArray): String
}