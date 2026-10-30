package com.musicpipe.player.ui.design

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.musicpipe.player.ui.theme.AwwMotion
import com.musicpipe.player.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * DESIGN.md §7 颗粒噪点层 —— Awwwards 观感的关键之一。
 *
 * 全站叠一层极淡噪点（默认 opacity 0.05），让纯色平面有"物质感"。
 *
 * Compose 实现要点：
 * 1. 噪点图**预生成一次**（[rememberNoiseBitmap]），绝不每帧画随机点 ——
 *    后者一屏上千个 drawCircle 会直接压垮 GPU（正是 v1.6.0 首页掉帧那类问题）。
 * 2. 用 [drawWithCache] 把这张图当 shader 平铺，整层只有一次 drawImage。
 * 3. **不拦截触摸**：这里不挂任何 pointerInput/clickable，放在内容之上也能穿透。
 * 4. 位移只用 transform（graphicsLayer.translationY），不触发重绘（§9）。
 * 5. 尊重 reduced-motion：开关打开时噪点静止（保留质感，去掉动作）。
 */
@Composable
fun GrainOverlay(
    modifier: Modifier = Modifier,
    opacity: Float = 0.05f
) {
    val reduceMotion = LocalReduceMotion.current
    val noise = rememberNoiseBitmap()
    // v1.14.0 性能修复（滚动卡顿第二处，比"每帧重组"更隐蔽）：
    // 原来用 rememberInfiniteTransition + animateFloat 驱动位移。即使已经把读值挪进
    // graphicsLayer（不再重组），infinite transition 仍然**每帧**推进一次动画值 ——
    // 于是这个铺满全屏的噪点层每帧都被标记为需要重绘，GPU 每帧重画一整屏。
    // 而它的视觉语义其实是 CSS steps(10) 8s：一秒只跳 1.25 次。
    //
    // 改为离散档位：LaunchedEffect 每 800ms 只更新一次 Int 状态，一帧都不多画。
    // 观感与 steps(10) 完全一致（本来就是跳变的），但重绘次数从 60/s 降到 1.25/s。
    var grainStep by remember { mutableIntStateOf(0) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        while (true) {
            delay(800L)
            grainStep = (grainStep + 1) % 10
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                // v1.14.0 性能修复（滚动卡顿的关键一处）：
                // 以前用 by 委托读动画值，且在**组合体**里算 floor(phase)，于是这层覆盖全屏的
                // 噪点每帧都重组一次（60 次/秒），每次重组都重建整条 Modifier 链与
                // drawWithCache 节点，结果整屏每帧重绘一遍噪点、叠在正在滚动的内容之上 ——
                // 表现就是"不管滑到哪都卡"。它挂在整棵树最外层（MusicPipeApp.kt:100），
                // 所以这个开销是全局的、与列表内容无关。
                //
                // 现在把读取挪进 graphicsLayer 的 lambda：这是 draw 阶段的读，Compose 只
                // 把该 layer 标记为需要更新，**不触发重组**、也不重建 Modifier。
                // 噪点位移本来就只有 transform，属于 §9 允许的动画方式。
                val step = if (reduceMotion) 0 else grainStep
                translationY = step * 13f
                alpha = opacity
            }
            .drawWithCache {
                val bmp = noise
                onDrawBehind {
                    drawImage(
                        image = bmp,
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(
                            size.width.toInt().coerceAtLeast(1),
                            size.height.toInt().coerceAtLeast(1)
                        )
                    )
                }
            }
    )
}

/** 128x128 确定性噪点图，整个进程只生成一次。 */
private var cachedNoise: ImageBitmap? = null

private fun rememberNoiseBitmap(): ImageBitmap {
    cachedNoise?.let { return it }
    val n = 128
    val rnd = Random(0x5EED)
    val pixels = IntArray(n * n)
    for (i in pixels.indices) {
        val v = rnd.nextInt(255)
        val a = rnd.nextInt(40, 200)
        pixels[i] = (a shl 24) or (v shl 16) or (v shl 8) or v
    }
    val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
    bmp.setPixels(pixels, 0, n, 0, 0, n, n)
    val img = bmp.asImageBitmap()
    cachedNoise = img
    return img
}

/**
 * §5 动效①：文字遮罩揭示。
 *
 * 遮罩从下往上揭开 + 文字从下方顶出（clip-path inset(100%->0) + translateY(110%->0)）。
 * [delayMillis] 用于 stagger（每行 +[AwwMotion.Stagger]）。
 *
 * 注：Compose 没有 clip-path，这里用 graphicsLayer.clip + translationY 模拟同一个观感，
 * 且**只动 transform**（§9 禁止对 height/top 做动画）。
 */
@Composable
fun MaskReveal(
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
    animate: Boolean = true,
    content: @Composable () -> Unit
) {
    val reduceMotion = LocalReduceMotion.current
    // 与 ClipReveal 同一处修复：Lazy 条目复用时会重播入场动画。
    val start = if (reduceMotion || !animate) 1f else 0f
    val progress = remember(animate) { Animatable(start) }
    androidx.compose.runtime.LaunchedEffect(animate) {
        if (reduceMotion || !animate) return@LaunchedEffect
        if (delayMillis > 0) delay(delayMillis.toLong())
        progress.animateTo(1f, tween(AwwMotion.DurReveal, easing = AwwMotion.EaseOutExpo))
    }
    Box(
        modifier.graphicsLayer {
            val v = progress.value
            clip = true
            translationY = (1f - v) * 30f
            alpha = v.coerceIn(0f, 1f)
        }
    ) { content() }
}

/**
 * §5 动效②：图片/区块入场 —— clip 收敛 + scale 1.12 -> 1。
 */
@Composable
fun ClipReveal(
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
    animate: Boolean = true,
    content: @Composable () -> Unit
) {
    val reduceMotion = LocalReduceMotion.current
    // v1.14.0 性能修复（网格滚动卡顿）：Reveal 在 Lazy 列表里会**随条目复用重复播放** ——
    // remember/LaunchedEffect 的存活期跟着组合走，卡片滑出屏幕被回收、滑回来重新组合时
    // 又是从 0f 开始 1200ms 动画。滚动过程中不断有新卡片入场，一屏最多 6 个 Animatable
    // 同时推进，逐帧写入 graphicsLayer，这就是"滑一下抖一下"的直接来源。
    //
    // animate=false 时直接从终态开始（progress 初值 1f），完全不创建动画。
    // 调用方（HomeScreen 的网格）只在首屏加载的头一个动画周期内传 true，之后一律 false：
    // 入场美感保留一次，滚动复用不再反复播。
    val start = if (reduceMotion || !animate) 1f else 0f
    val p = remember(animate) { Animatable(start) }
    androidx.compose.runtime.LaunchedEffect(animate) {
        if (reduceMotion || !animate) return@LaunchedEffect
        if (delayMillis > 0) delay(delayMillis.toLong())
        p.animateTo(1f, tween(AwwMotion.DurReveal, easing = AwwMotion.EaseOutExpo))
    }
    Box(
        modifier.graphicsLayer {
            val v = p.value
            scaleX = 1f + (1f - v) * 0.12f
            scaleY = 1f + (1f - v) * 0.12f
            alpha = v.coerceIn(0f, 1f)
        }
    ) { content() }
}

/**
 * §5 动效⑤：按压反馈（触屏没有 hover，磁吸的等价物是"按下内缩"）。
 *
 * 只改 graphicsLayer 的 scale，不触发子内容重组。
 */
@Composable
fun PressScale(
    modifier: Modifier = Modifier,
    pressedScale: Float = 0.975f,
    content: @Composable () -> Unit
) {
    val reduceMotion = LocalReduceMotion.current
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) pressedScale else 1f,
        animationSpec = tween(AwwMotion.DurFast, easing = AwwMotion.EaseOutQuint),
        label = "pressScale"
    )
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .pointerInput(reduceMotion) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        pressed = event.changes.any { it.pressed }
                    }
                }
            }
    ) { content() }
}
