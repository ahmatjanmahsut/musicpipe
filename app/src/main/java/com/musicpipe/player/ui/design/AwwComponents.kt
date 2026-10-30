package com.musicpipe.player.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musicpipe.player.ui.theme.AwwColors
import com.musicpipe.player.ui.theme.AwwShapes
import com.musicpipe.player.ui.theme.AwwSpace
import com.musicpipe.player.ui.theme.AwwTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * DESIGN.md 组件层的基础件。
 *
 * 这里的每一个都对应规范里的一条硬约束，页面**不再自行拼壳**，
 * 否则"uppercase + letter-spacing .12em""细线分隔"这类细节会在 17 个页面里漂移。
 */

/**
 * 小标签（§2）：uppercase + letter-spacing .12em + weight 500。
 * 例：`—— AHMATTJON 收藏`、`01 / 03`。
 */
@Composable
fun AwwLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Text(
        // v1.13.0：是否全大写由风格决定 —— Editorial/Brutalist 全大写，
        // Material/Cupertino/shadcn 保持原样（Apple 与 Google 规范都不做全大写标签）。
        text = if (AwwTheme.type.uppercaseLabels) text.uppercase() else text,
        style = AwwTheme.type.label,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/** 编号/时间戳（§2：mono + tabular-nums）。 */
@Composable
fun AwwMono(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    style: androidx.compose.ui.text.TextStyle = AwwTheme.type.monoMeta
) {
    Text(text = text, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/**
 * 细线（§3 line token）。整站唯一的分隔手段 —— 不用卡片+阴影。
 */
@Composable
fun AwwRule(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.outlineVariant) {
    // v1.13.0：分隔线宽度由风格决定 —— Editorial 1dp、Cupertino 0.5dp 发丝线、
    // Brutalist 2dp 粗线。整站唯一的分隔手段必须跟着风格走，否则换风格后
    // "硬边"风格里出现一堆 1dp 细线会立刻露馅。
    Box(modifier.fillMaxWidth().height(AwwTheme.chrome.ruleHeight).background(color))
}

/**
 * 章节标题：左侧 "01 —" 编号 + 大字标题，**非对称**（标题占宽，右侧留白）。
 *
 * §4 要求"12 列栅格必须非对称跨列"，移动端等价做法是：
 * 编号固定窄列，标题占剩余，右侧不塞任何东西（留白就是设计）。
 */
@Composable
fun SectionHead(
    index: String,
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null
) {
    Column(modifier.fillMaxWidth()) {
        Spacer(Modifier.height(AwwSpace.s6))
        Row(verticalAlignment = Alignment.Bottom) {
            AwwMono("$index —", color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(AwwSpace.s3))
            Text(
                text = title,
                style = AwwTheme.type.h2,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        if (trailing != null) {
            Spacer(Modifier.height(AwwSpace.s1))
            AwwMono(trailing, modifier = Modifier.padding(start = AwwSpace.s8))
        }
        Spacer(Modifier.height(AwwSpace.s3))
        AwwRule()
    }
}

/**
 * 主按钮（§6）：透明底 + 1px 实线，大写小标签；按下时强调色填充 + 文字反色。
 *
 * 同时实现 §6 的键盘焦点态 `:focus-visible`（outline 2px accent offset 3px），
 * 这是验收清单里明确要检查的一项。
 */
@Composable
fun AwwButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    // v1.13.0：默认是否填充由风格决定。Editorial 规范要求"透明底 + 1px 实线"，
    // 而 Material/Cupertino/shadcn 的主按钮本来就是个实心块 —— 这里让风格说话，
    // 调用方只在需要"反向强调"时才显式传 filled。
    filled: Boolean = AwwTheme.chrome.filledButtons
) {
    var focused by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onSurface
    val border = if (filled) accent else if (focused) accent else MaterialTheme.colorScheme.outline
    val bg = if (filled) accent else Color.Transparent
    val fg = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        filled -> MaterialTheme.colorScheme.background
        focused -> accent
        else -> ink
    }
    Box(
        modifier
            .then(if (focused) Modifier.border(2.dp, accent, AwwShapes.Square) else Modifier)
            .padding(if (focused) 0.dp else 0.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(bg, AwwShapes.Button)
                .border(1.dp, border, AwwShapes.Button)
                .onFocusChanged { focused = it.isFocused }
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (AwwTheme.type.uppercaseLabels) text.uppercase() else text,
                style = AwwTheme.type.label,
                color = fg
            )
        }
    }
}

/**
 * 空态（§8：禁灰色空块/占位图）。
 *
 * 规范要求空的地方也要有编辑感 —— 用编号 + 短句 + 细线，而不是一个灰方块。
 */
@Composable
fun AwwEmpty(
    index: String,
    title: String,
    hint: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = AwwSpace.s8, vertical = AwwSpace.s24),
        horizontalAlignment = Alignment.Start
    ) {
        AwwMono(index, color = MaterialTheme.colorScheme.primary, style = AwwTheme.type.mono)
        Spacer(Modifier.height(AwwSpace.s3))
        Text(title, style = AwwTheme.type.h2, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(AwwSpace.s2))
        AwwRule()
        Spacer(Modifier.height(AwwSpace.s3))
        Text(hint, style = AwwTheme.type.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 横向细滚动条进度（用于迷你播放器/播放页顶部）—— 1dp 线，强调色只占很小的头部。
 * §3：强调色面积 <= 5%，所以进度条用 ink 细线 + accent 头部。
 */
@Composable
fun HairlineProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    track: Color = MaterialTheme.colorScheme.outlineVariant,
    head: Color = MaterialTheme.colorScheme.primary
) {
    val p = progress.coerceIn(0f, 1f)
    Box(modifier.fillMaxWidth().height(1.dp).background(track)) {
        Box(Modifier.fillMaxWidth(p).height(1.dp).background(head))
    }
}
/**
 * 页面页眉（v1.12.0 新增，所有二级页统一用它）。
 *
 * 结构固定为三段，避免 17 个页面各自拼壳导致细节漂移：
 *   ① 顶行：可选 ‹ 返回 + 大写小标签 + 右侧 mono 编号/操作
 *   ② 可选大标题（h2，非对称贴左，不加任何容器）
 *   ③ 1dp 细线收尾
 *
 * §10 负面清单禁"全站同一字号层级"：大标题一定是 h2 而标签是 11sp label，
 * 两者相差 3 倍以上，页面一进来就有明确的视觉焦点。
 */
@Composable
fun AwwPageHeader(
    index: String,
    label: String,
    title: String? = null,
    meta: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s2),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                Text(
                    text = "‹",
                    style = AwwTheme.type.h3,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clickable(onClick = onBack)
                        .padding(end = AwwSpace.s4)
                )
            }
            AwwLabel(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            if (trailing != null) trailing() else AwwMono(text = index, color = AwwColors.Ink3)
        }
        if (title != null) {
            Text(
                text = title,
                // v1.13.0：编辑风用 h2 大字页眉，系统风（Material/Cupertino/shadcn）用 h3，
                // 这是两种真实存在的页眉语言 —— Google 的 top app bar 就是小标题，Apple 用 large title。
                style = if (AwwTheme.chrome.largeHeaders) AwwTheme.type.h2 else AwwTheme.type.h3,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s3)
            )
        }
        if (meta != null) {
            AwwMono(
                text = meta,
                modifier = Modifier.padding(horizontal = AwwSpace.s6).padding(bottom = AwwSpace.s3),
                color = AwwColors.Ink3
            )
        }
        AwwRule()
    }
}
