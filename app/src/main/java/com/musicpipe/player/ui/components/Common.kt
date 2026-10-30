package com.musicpipe.player.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.musicpipe.player.ui.design.AwwMono
import com.musicpipe.player.ui.design.AwwRule
import com.musicpipe.player.ui.theme.AwwMotion
import com.musicpipe.player.ui.theme.AwwSpace
import com.musicpipe.player.ui.theme.AwwTheme
import com.musicpipe.player.ui.theme.LocalReduceMotion

/**
 * 加载态（DESIGN.md §6 "加载页 0-100 计数（mono）+ 品牌名，完成后遮罩上滑"）。
 *
 * 已经**没有 CircularProgressIndicator**：
 * 规范禁的是"无意义的转圈"，而编辑感的加载是"编号在跳 + 一条细线在长"。
 * 计数用 mono 字体（§2：数字一律 mono），并且只改 alpha 不动布局（§9）。
 */
@Composable
fun LoadingBlock(modifier: Modifier = Modifier, delayMillis: Long = LOADING_DELAY_MS) {
    // v1.15.0：延迟门。
    // 多数加载（本地快照、缓存命中）在几十毫秒内就结束，立即画计数器等于在用户眼前闪一下
    // 000→099 再消失，观感比空白更差。等过 delayMillis 才真正开始计数，快路径直接不出现。
    var visible by remember { mutableStateOf(delayMillis <= 0L) }
    if (!visible) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(delayMillis)
            visible = true
        }
        Box(modifier)
        return
    }
    val reduceMotion = LocalReduceMotion.current
    val transition = rememberInfiniteTransition(label = "loading")
    val tick by transition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing)
        ),
        label = "tick"
    )
    val shown = if (reduceMotion) 42 else tick.toInt()

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.padding(horizontal = AwwSpace.gutter(360))
        ) {
            Text(
                text = "MTMUSIC",
                style = AwwTheme.type.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(AwwSpace.s2))
            AwwMono(
                text = shown.toString().padStart(3, '0'),
                style = AwwTheme.type.display,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(AwwSpace.s3))
            AwwRule()
        }
    }
}

/** 加载指示器的延迟门（毫秒）。低于这个时长的加载不显示任何指示器，避免闪烁。 */
const val LOADING_DELAY_MS = 220L

/**
 * v1.15.0：**长时间**等待曲库时的静态状态块。
 *
 * 为什么不用 [LoadingBlock]：计数器是循环动画，等上十几秒时就变成用户口中的
 * "圈一直在转"。这里刻意做成完全静止的一屏（只有位置固定的编号 000 / 标题 / 细线），
 * 一眼能看出"在同步但没卡死"，且不消耗任何动画帧。
 */
@Composable
fun SyncingBlock(
    title: String = "正在同步曲库",
    hint: String = "首次启动需要拉取曲库元数据；网络较慢时可先下拉重试。",
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.padding(horizontal = AwwSpace.gutter(360))
        ) {
            Text(
                text = "MTMUSIC",
                style = AwwTheme.type.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(AwwSpace.s2))
            AwwMono(
                text = "000",
                style = AwwTheme.type.display,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(AwwSpace.s3))
            AwwRule()
            Spacer(Modifier.height(AwwSpace.s4))
            Text(
                text = title,
                style = AwwTheme.type.h3,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(AwwSpace.s2))
            Text(
                text = hint,
                style = AwwTheme.type.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 错误/降级横幅。
 *
 * 改成"左侧强调色竖线 + 文字"—— 不用红底块（§3：强调色面积 <= 5%）。
 */
@Composable
fun ErrorBanner(text: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = AwwSpace.gutter(360), vertical = AwwSpace.s3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(2.dp)
                .height(28.dp)
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(AwwSpace.s3))
        Text(
            text = text,
            style = AwwTheme.type.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 空态的极简版（页面自己知道编号时请用 design/AwwComponents 的 AwwEmpty）。 */
@Composable
fun EmptyBlock(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = AwwTheme.type.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = AwwSpace.s8)
        )
    }
}
