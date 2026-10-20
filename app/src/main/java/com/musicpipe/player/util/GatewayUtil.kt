package com.musicpipe.player.util

/** Pure helpers for the /api/v1 gateway surface (unit tested: GatewayUtilTest). */
object GatewayUtil {

    /** Platform code -> display name (order = chip order). */
    val PLATFORMS: List<Pair<String, String>> = listOf(
        "kw" to "酷我",
        "kg" to "酷狗",
        "tx" to "QQ音乐",
        "wy" to "网易"
    )

    /**
     * platforms query param: null when nothing selected or all four selected —
     * the gateway defaults to searching all platforms then.
     */
    fun platformsParam(selected: Set<String>): String? {
        val codes = PLATFORMS.map { it.first }
        val valid = selected.filter { it in codes }
        return if (valid.isEmpty() || valid.size == codes.size) null
        else valid.sorted().joinToString(",")
    }

    /** Download qualities offered by POST /api/v1/download. */
    val QUALITIES: List<String> = listOf("auto", "flac", "320k", "128k")

    fun qualityLabel(q: String?): String = when (q) {
        null, "" -> "自动"
        "auto" -> "自动"
        "flac" -> "无损FLAC"
        "320k" -> "320kbps"
        "128k" -> "128kbps"
        else -> q
    }

    fun isActive(status: String?): Boolean =
        status in setOf("pending", "queued", "downloading", "organizing")

    fun isRemovable(status: String?): Boolean =
        status in setOf("done", "imported", "failed", "cancelled")

    fun statusLabel(status: String?): String = when (status) {
        "pending", "queued" -> "排队中"
        "downloading" -> "下载中"
        "organizing" -> "整理中"
        "done" -> "已完成"
        "imported" -> "已入库"
        "failed" -> "失败"
        "cancelled" -> "已取消"
        null, "" -> "-"
        else -> status
    }

    /**
     * v1.6.0 需求2：把路由器返回的下载失败原文翻译成**可操作**的提示。
     *
     * 用户报的具体现象是「音质选择自动时提示校验失败：无法识别的音频头和文件头（mp3）
     * 与扩展名（flac）不符」。服务端排查结论：音源对"该曲目不支持的音质"会返回垃圾字节流
     * （例如要求 flac 但曲目只有 128k 时，酷我返回纯文本 "refuse request!" 或一段非音频流），
     * 于是文件头校验失败。这是**音质与曲目能力不匹配**，不是 App 的 bug —— 但原来的
     * 报错把人引向"文件坏了"，用户只会反复重试。这里给出下一步该怎么做。
     *
     * 纯函数，便于单测（GatewayUtilTest）。
     */
    fun humanizeTaskError(raw: String?): String? {
        val msg = raw?.trim().orEmpty()
        if (msg.isEmpty()) return null
        return when {
            msg.contains("无法识别的音频") || msg.contains("文件头") ->
                "该曲目没有所选音质的资源，音源返回的不是音频文件。请把音质改成「自动」或 128kbps 后重试。"
            msg.contains("文件过小") || msg.contains("试听") ->
                "音源只提供了试听片段，无法完整下载。可换一个平台或改用其它音质重试。"
            msg.contains("取链失败") || msg.contains("全部") && msg.contains("音源") ->
                "所有音源都取不到播放地址（可能是版权限制或链接过期）。请稍后重试或换一首。"
            msg.contains("WebDAV") || msg.contains("网盘") ->
                "路由器的网盘未配置或不可用，请先在 Web 控制台的设置里检查网盘连接。"
            else -> msg
        }
    }

    /** sharelink song states -> labels. */
    fun shareSongStateLabel(state: String?, inLib: Int): String = when {
        inLib == 1 -> "已在库"
        state == "queued" -> "排队中"
        state == "done" -> "已导入"
        state == "failed" -> "失败"
        state == "exists" -> "已存在"
        else -> "待导入"
    }
}
