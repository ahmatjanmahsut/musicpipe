package com.musicpipe.player.util

import java.io.File
import java.security.MessageDigest

object Md5 {
    /** 32-char lowercase hex md5 of a string (UTF-8). */
    fun hex(input: String): String = digest(MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8)))

    /** 32-char lowercase hex md5 of a file, streamed (for APK verification, docs/01 §6). */
    fun file(file: File): String {
        val md = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return digest(md.digest())
    }

    private fun digest(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }
}
