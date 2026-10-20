package ru.drevoinfo.db

import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

/** The `ziptext` encoding: a zlib stream (RFC 1950) of the UTF-8 text. */
object ZipText {
    fun zip(text: String): ByteArray {
        val bytes = ByteArrayOutputStream()
        DeflaterOutputStream(bytes).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return bytes.toByteArray()
    }

    fun unzip(bytes: ByteArray): String = InflaterInputStream(bytes.inputStream()).use { it.readBytes() }.toString(Charsets.UTF_8)
}
