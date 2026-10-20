package com.musicpipe.player.data.model

import com.google.gson.JsonObject

/**
 * Read-only display wrapper over a raw gateway search song.
 * The original JsonObject is kept so POST /api/v1/download can send it back verbatim.
 */
class NetSong(val json: JsonObject) {

    private fun str(key: String): String = try {
        json.get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    } catch (_: Throwable) {
        ""
    }

    val source: String get() = str("source")
    val id: String get() = str("id")
    val name: String get() = str("name")
    val singer: String get() = str("singer")
    val album: String get() = str("album")

    val durationSec: Long get() = try {
        json.get("duration")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L
    } catch (_: Throwable) {
        0L
    }

    val img: String? get() = str("img").ifBlank { null }

    val qualityTypes: List<String> get() = try {
        json.getAsJsonArray("types")?.mapNotNull { e ->
            e?.takeIf { it.isJsonObject }?.asJsonObject?.get("type")
                ?.takeIf { it.isJsonPrimitive }?.asString
        } ?: emptyList()
    } catch (_: Throwable) {
        emptyList()
    }

    /** Stable identity for selection sets: "source:id". */
    val key: String get() = source + ":" + id

    /**
     * 逐条音质可用性：type -> 是否为 "最高音质" 标记。
     *
     * 音源返回的 types 里每个元素形如 {type:"flac", size:"..."}，但各平台并非都能提供全部音质。
     * v1.6.0 起下载前用它把不可用的音质置灰，避免用户选了"自动/无损"后拿到降级文件
     * （降级文件曾是「文件头(mp3)与扩展名(flac)不符」的来源之一）。
     */
    val availableTypes: Set<String> get() = qualityTypes.map { it.lowercase() }.toSet()

    /** 该平台是否提供选定音质；"auto" 恒为可用（服务端会自行选最优）。 */
    fun supportsQuality(q: String): Boolean {
        if (q == "auto" || q.isBlank()) return true
        if (availableTypes.isEmpty()) return true // 信息缺失时不误判，交给服务端决定
        val want = q.lowercase()
        return when (want) {
            "320k" -> availableTypes.any { it == "320k" || it == "flac" || it == "flac24bit" || it == "hires" }
            "128k" -> availableTypes.any { it == "128k" || it == "320k" || it == "flac" || it == "flac24bit" }
            "flac" -> availableTypes.any { it == "flac" || it == "flac24bit" }
            else -> want in availableTypes
        }
    }
}
