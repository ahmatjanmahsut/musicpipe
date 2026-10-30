package com.musicpipe.player.media.car

/**
 * 车载歌词的三种开启方式。
 *
 * 默认 [AUTO]：只在判定为"蓝牙车机"时注入歌词行。
 * 之所以给 [ALWAYS]，是因为**设备名分类一定会漏**（很多后装车机的蓝牙名就是 "Car Multimedia"），
 * 用户能自己钉死；而 [OFF] 是给"我就想让它只显示歌名"的人。
 */
enum class CarLyricsMode(val id: String) {
    OFF("off"), AUTO("auto"), ALWAYS("always");

    val label: String
        get() = when (this) {
            OFF -> "关闭"
            AUTO -> "自动（识别到车机时）"
            ALWAYS -> "始终开启"
        }

    companion object {
        fun fromId(raw: String?): CarLyricsMode =
            entries.firstOrNull { it.id == (raw ?: "").trim() } ?: AUTO
    }
}

/**
 * 歌词行写进哪个元数据字段。
 *
 * 这是本功能的核心不确定点：**AVRCP 没有"歌词"这个字段**，
 * 车机屏幕上能显示的一切都来自媒体元数据的 title / artist / album 三行。
 * 不同车机的播放页挑的字段不一样（有的只画 title+artist，有的才显示 album），
 * 所以这里三种都给，让用户在车里试一次就能定下来。
 */
enum class CarLyricsLayout(val id: String) {
    /** 歌词行占「标题」行，歌名挪到「歌手」行 —— 兼容性最好（任何车机都画标题）。 */
    TITLE("title"),
    /** 歌名留在「标题」，歌词行占「歌手」行。 */
    ARTIST("artist"),
    /** 歌名/歌手都不动，歌词行占「专辑」行 —— 通知栏最干净。 */
    ALBUM("album");

    val label: String
        get() = when (this) {
            TITLE -> "标题行（兼容性最好）"
            ARTIST -> "歌手行（保留歌名）"
            ALBUM -> "专辑行（通知栏最干净）"
        }

    companion object {
        fun fromId(raw: String?): CarLyricsLayout =
            entries.firstOrNull { it.id == (raw ?: "").trim() } ?: TITLE
    }
}

/** 车载歌词的全部用户设置。 */
data class CarLyricsSettings(
    val mode: CarLyricsMode = CarLyricsMode.AUTO,
    val layout: CarLyricsLayout = CarLyricsLayout.TITLE,
    /**
     * 延迟补偿（毫秒，正数 = 提前发送）。
     *
     * 蓝牙 A2DP 从手机编码到车机出声通常有 150~400ms 延迟，而车机刷新的那一行
     * 是"刚才那 200ms 里最后收到的元数据"。不补偿就会出现"声音唱到这里、屏幕还停在上一句"。
     */
    val offsetMs: Long = 250L,
    /** 单行最大字符数。车机对长行的处理各不相同（有的截断、有的滚动、有的直接不显示）。 */
    val maxChars: Int = 48
) {
    fun isActive(output: AudioOutputState): Boolean = when (mode) {
        CarLyricsMode.OFF -> false
        CarLyricsMode.ALWAYS -> true
        CarLyricsMode.AUTO -> output.kind == AudioOutputKind.BT_CAR
    }

    companion object {
        /** 单行字数下限：再短就只剩半句，没有意义。 */
        const val MIN_CHARS = 12
        /** 上限：太长的行部分车机会直接不显示（而不是滚动）。 */
        const val MAX_CHARS = 120
    }
}
