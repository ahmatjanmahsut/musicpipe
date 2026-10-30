package com.musicpipe.player.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 动效 token —— 时长跟随当前风格。
 *
 * Editorial 的缓动是慢而重的（450ms base），Material 是标准的 300ms，
 * shadcn/桌面组件库就要快（200ms base），否则交互感觉"迟钝"。
 * 缓动曲线保持 Awwwards 那三条不变：它们本身是中性的快出慢收，
 * 任何风格用它都不会显得突兀，而换曲线会让动效"性格"打架。
 */
object AwwMotion {

    /** cubic-bezier(.16,1,.3,1) —— 主力缓动，快出慢收。 */
    val EaseOutExpo: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    /** cubic-bezier(.22,1,.36,1)。 */
    val EaseOutQuint: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    /** cubic-bezier(.76,0,.24,1)。 */
    val EaseInOutQuart: Easing = CubicBezierEasing(0.76f, 0f, 0.24f, 1f)

    val DurFast: Int get() = StyleState.spec.motion.fast
    val DurBase: Int get() = StyleState.spec.motion.base
    val DurSlow: Int get() = StyleState.spec.motion.slow

    /** 入场揭示的时长：固定比 slow 更长（编辑风的电影感来自这个 1.2s 的长揭示）。 */
    val DurReveal: Int get() = (StyleState.spec.motion.slow * 1.5f).toInt()

    /** 文字/图片交错入场间隔。 */
    val Stagger: Int get() = (StyleState.spec.motion.fast / 3.2f).toInt().coerceAtLeast(20)
}

/**
 * 无障碍降级开关（DESIGN.md §5 的 prefers-reduced-motion）。
 *
 * Android 没有 CSS 的 media query，等价物是系统设置里的
 * "移除动画"（Settings > Accessibility > Remove animations），
 * 它会把 Settings.Global.ANIMATOR_DURATION_SCALE 置 0。
 * 在那里为 true 时，所有入场动效直接跳终态、噪点层停止位移动画。
 */
val LocalReduceMotion = staticCompositionLocalOf { false }