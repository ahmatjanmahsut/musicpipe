package com.musicpipe.player.util

/**
 * 零中间集合的 " · " 元信息拼接。
 *
 * v1.14.0：列表行里原本到处是
 * `buildList { ... }.joinToString(" · ")` 或 `listOf(a, b).filter { }.joinToString(" · ")`。
 * 那样每行每次重组要分配：ArrayList（含扩容）+ 迭代器 + 每个元素装箱引用 + 最终 String，
 * 一屏十几行、滚动时每帧都在造，累计起来就是稳定的小额 GC 抖动。
 *
 * 这里只有一个 StringBuilder：先按调用顺序 append，[build] 时一次性 toString。
 * 空串/纯空白的分段自动跳过（等价于原来的 filter { it.isNotBlank() }）。
 */
class MetaJoin(private val separator: String) {
    private var sb: StringBuilder? = null
    private var count = 0

    fun add(part: String?) {
        if (part == null || part.isBlank()) return
        val b = sb ?: StringBuilder(32).also { sb = it }
        if (count > 0) b.append(separator)
        b.append(part)
        count++
    }

    fun build(): String {
        val b = sb ?: return ""
        return b.toString()
    }
}

/** 用法：`metaText { add(artist); add(album) }`。 */
inline fun metaText(separator: String = " · ", block: MetaJoin.() -> Unit): String {
    val j = MetaJoin(separator)
    j.block()
    return j.build()
}
