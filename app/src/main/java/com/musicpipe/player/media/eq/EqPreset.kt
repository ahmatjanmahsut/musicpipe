package com.musicpipe.player.media.eq

/**
 * 预设面向的输出场景。用来做"当前输出设备 → 建议预设"的推荐，
 * 也用来给 UI 分组（车载 / 耳机 / 通用）。
 */
enum class EqTargetDevice { GENERIC, CAR, HEADPHONE }

/**
 * 一套均衡设置。
 *
 * 约定：**内置预设的 gains 一律是 [EqBands.canonical] 的 5 段**，
 * 运行时由 [EqBands.resample] 映射到设备真实频段；用户自定义预设也是 5 段，
 * 于是"曲线"这个概念在整条链路上只有一种表示。
 */
data class EqPreset(
    val id: String,
    val name: String,
    val tagline: String,
    val gains: List<Float>,
    val makeupDb: Float = 0f,
    val hint: EqTargetDevice = EqTargetDevice.GENERIC,
    /** 内置预设不可删除/重命名。 */
    val builtIn: Boolean = true,
)

/** 当前生效的设置（持久化用）。presetId 为 null 表示用户手动改过、不对应任何预设。 */
data class EqProfile(
    val presetId: String? = EqPresets.FLAT_ID,
    val gains: List<Float> = EqBands.zero,
    val makeupDb: Float = 0f,
)

object EqPresets {

    const val FLAT_ID = "flat"
    const val CAR_MAZDA_ID = "car_mazda"
    const val CAR_GENERIC_ID = "car_generic"
    const val HP_SONY = "hp_sony_wh1000xm5"
    const val HP_GENERIC_ID = "hp_generic"
    const val VOCAL_ID = "vocal"
    const val BASS_ID = "bass"
    const val TREBLE_ID = "treble"

    /**
     * 内置预设表。
     *
     * 曲线都是"补偿式"的：先看这类输出的**已知缺陷**，再决定往哪儿推。
     *  - Mazda 车机：车内近场反射多、A2DP 走 SBC/AAC 削高频，车门在 60~120Hz 有共振，
     *    所以削 60Hz、抬 3.6k 的人声清晰度、轻削 14k 的压缩毛刺，并靠 +2dB makeup 补回整体响度。
     *  - SONY WH-1000XM5：耳机本身低频偏多（尤其降噪开启时），人声略后；
     *    所以削一点 60Hz、抬 910~3.6k，14k 只轻抬（XM5 极高频并不缺）。
     * 数值是**起始点**而不是"唯一正确答案"：用户可在此基础上继续手动微调并另存。
     */
    val builtIns: List<EqPreset> = listOf(
        EqPreset(
            id = FLAT_ID,
            name = "平直",
            tagline = "不染色 · 参考基准线",
            gains = listOf(0f, 0f, 0f, 0f, 0f),
            makeupDb = 0f
        ),
        EqPreset(
            id = CAR_MAZDA_ID,
            name = "Mazda 车机",
            tagline = "车内共振补偿 · 人声靠前 · 蓝牙压缩补偿",
            gains = listOf(-3f, -1.5f, 2f, 3f, -1.5f),
            makeupDb = 2f,
            hint = EqTargetDevice.CAR
        ),
        EqPreset(
            id = CAR_GENERIC_ID,
            name = "车载通用",
            tagline = "车内声学通用补偿 · 削低频轰鸣",
            gains = listOf(-2.5f, -1f, 1.5f, 2.5f, -1f),
            makeupDb = 1.5f,
            hint = EqTargetDevice.CAR
        ),
        EqPreset(
            id = HP_SONY,
            name = "SONY WH-1000XM5",
            tagline = "削耳机低频堆积 · 换回人声清晰度",
            gains = listOf(-2f, 0.5f, 1.5f, 2f, 1f),
            makeupDb = 0f,
            hint = EqTargetDevice.HEADPHONE
        ),
        EqPreset(
            id = HP_GENERIC_ID,
            name = "蓝牙耳机通用",
            tagline = "轻 V 形 · 兼顾低频与人声",
            gains = listOf(2f, 0f, 0.5f, 1.5f, 2f),
            makeupDb = 0f,
            hint = EqTargetDevice.HEADPHONE
        ),
        EqPreset(
            id = VOCAL_ID,
            name = "人声 / 播客",
            tagline = "中频提升 · 削掉低频轰鸣",
            gains = listOf(-4f, -2f, 3f, 3.5f, 0f),
            makeupDb = 1f
        ),
        EqPreset(
            id = BASS_ID,
            name = "低频增强",
            tagline = "贝斯与底鼓更实 · 建议配耳机",
            gains = listOf(6f, 4f, 1f, 0f, -1f),
            makeupDb = 0f
        ),
        EqPreset(
            id = TREBLE_ID,
            name = "高频明亮",
            tagline = "细节与空气感 · 车内可补偿喇叭发闷",
            gains = listOf(-2f, -1f, 0f, 2f, 4f),
            makeupDb = 0f
        ),
    )

    fun byId(id: String?): EqPreset? = builtIns.firstOrNull { it.id == id }

    fun default(): EqPreset = builtIns.first()

    /** 名称（找不到时回落到"自定义"）。 */
    fun nameOf(id: String?, custom: List<EqPreset> = emptyList()): String =
        (custom.firstOrNull { it.id == id } ?: byId(id))?.name ?: "自定义"

    /**
     * 按输出设备给建议预设。
     *
     * 这里只做**建议**（UI 上给一键应用），不自动改用户设置 ——
     * 自动改设置会让"我明明选过为什么又变了"发生，除非用户显式打开"跟随设备自动切换"。
     */
    fun forOutput(target: EqTargetDevice, deviceName: String = ""): EqPreset {
        val n = deviceName.lowercase()
        return when (target) {
            EqTargetDevice.CAR ->
                if (MAZDA_KEYS.any { n.contains(it) }) byId(CAR_MAZDA_ID)!! else byId(CAR_GENERIC_ID)!!
            EqTargetDevice.HEADPHONE ->
                if (SONY_KEYS.any { n.contains(it) }) byId(HP_SONY)!! else byId(HP_GENERIC_ID)!!
            EqTargetDevice.GENERIC -> default()
        }
    }

    private val MAZDA_KEYS = listOf("mazda", "mzd", "cmu")
    private val SONY_KEYS = listOf("sony", "wh-1000", "wh1000", "wf-1000", "wf1000")
}

/**
 * 自定义预设 / 手动增益的持久化编解码。
 *
 * 刻意不用 JSON 库：一是不想为一条 5 个数字的记录引入序列化约定，
 * 二是 DataStore 里存的字符串将来可能被人手改，格式越简单越不容易炸。
 *
 * 格式（每行一个预设，字段用 \t 分隔）：
 *   id \t name \t tagline \t g1,g2,g3,g4,g5 \t makeupDb
 */
object EqPresetCodec {

    private const val SEP = "\t"

    fun encodeGains(gains: List<Float>): String =
        EqBands.normalize(gains).joinToString(",") { trim(it) }

    fun decodeGains(raw: String?): List<Float> {
        if (raw.isNullOrBlank()) return EqBands.zero
        val parsed = raw.split(",").mapNotNull { it.trim().toFloatOrNull() }
        return EqBands.normalize(parsed)
    }

    fun encodeCustom(presets: List<EqPreset>): String = presets.joinToString("\n") { p ->
        listOf(
            p.id,
            sanitize(p.name),
            sanitize(p.tagline),
            encodeGains(p.gains),
            trim(p.makeupDb)
        ).joinToString(SEP)
    }

    fun decodeCustom(raw: String?): List<EqPreset> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split("\n").mapNotNull { line ->
            val f = line.split(SEP)
            if (f.size < 5) return@mapNotNull null
            val id = f[0].trim()
            if (id.isEmpty()) return@mapNotNull null
            EqPreset(
                id = id,
                name = f[1].ifBlank { "自定义" },
                tagline = f[2],
                gains = decodeGains(f[3]),
                makeupDb = EqBands.clampMakeup(f[4].trim().toFloatOrNull() ?: 0f),
                hint = EqTargetDevice.GENERIC,
                builtIn = false
            )
        }
    }

    /** 名称里不能出现分隔符换行，否则整行会被拆错。 */
    private fun sanitize(s: String): String =
        s.replace("\t", " ").replace("\n", " ").replace("\r", " ").trim()

    private fun trim(v: Float): String {
        val r = Math.round(v * 10f) / 10f
        return if (r == r.toInt().toFloat()) r.toInt().toString() else r.toString()
    }
}
