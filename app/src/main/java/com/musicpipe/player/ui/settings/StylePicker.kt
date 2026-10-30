package com.musicpipe.player.ui.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicpipe.player.ui.design.AwwLabel
import com.musicpipe.player.ui.design.AwwMono
import com.musicpipe.player.ui.theme.AppStyle
import com.musicpipe.player.ui.theme.AwwColors
import com.musicpipe.player.ui.theme.AwwSpace
import com.musicpipe.player.ui.theme.StylePalette
import com.musicpipe.player.ui.theme.StyleShape
import com.musicpipe.player.ui.theme.StyleSpecs

/**
 * 界面风格选择器（v1.13.0）。
 *
 * 每张卡片画的是**该风格自己**的迷你预览 —— 用 StyleSpecs 里那份调色板和圆角，
 * 而不是当前的 token。否则你在 Editorial 下看到的六张预览会全是暗底橙字，
 * 没法比较。这是本文件唯一直读 StyleSpecs 而不是走 AwwColors 的地方。
 *
 * 预览的明暗跟随当前主题：你在深色模式下比较的就是各风格的深色版。
 */
@Composable
fun StylePicker(
current: AppStyle,
dark: Boolean,
onSelect: (AppStyle) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        AppStyle.entries.forEach { style ->
            StyleCard(
                style = style,
                dark = dark,
                selected = style == current,
                onClick = { onSelect(style) }
            )
            Spacer(Modifier.height(AwwSpace.s2))
        }
    }
}

@Composable
private fun StyleCard(
    style: AppStyle,
    dark: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    val spec = remember(style) { StyleSpecs.of(style) }
    val pal = spec.palette(dark)
    val shape = spec.shape

    // 卡片外壳跟随**当前**风格（用户正在用的界面语言），
    // 内部预览块跟随**被预览**的风格 —— 这样既能融入当前界面，又能看清差异。
    val outerShape = remember { RoundedCornerShape(2.dp) }
    val borderColor = if (selected) AwwColors.Accent else AwwColors.Line

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = AwwSpace.s6)
            .border(if (selected) 2.dp else 1.dp, borderColor, outerShape)
            .clickable(onClick = onClick)
            .padding(AwwSpace.s3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StylePreview(pal = pal, shape = shape, spec = spec, modifier = Modifier.size(width = 64.dp, height = 48.dp))
        Spacer(Modifier.width(AwwSpace.s3))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AwwLabel(text = style.displayName, color = AwwColors.Ink)
                if (selected) {
                    Spacer(Modifier.width(AwwSpace.s2))
                    AwwMono(text = "✓ 使用中", color = AwwColors.Accent)
                }
            }
            Spacer(Modifier.height(AwwSpace.s1))
            Text(
                text = style.tagline,
                style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, lineHeight = 15.sp),
                color = AwwColors.Ink3,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 迷你预览：底色 + 两行"文字条" + 一个强调色圆点 + 一张卡片块。
 *
 * 用色块而不是真文字：48dp 高里塞文字会糊成一团，色块关系和圆角更能体现风格差异。
 */
@Composable
private fun StylePreview(
    pal: StylePalette,
    shape: StyleShape,
    spec: com.musicpipe.player.ui.theme.StyleSpec,
    modifier: Modifier = Modifier
) {
    val cardShape = remember(spec.style) { RoundedCornerShape(shape.card) }
    val inputShape = remember(spec.style) { RoundedCornerShape(shape.input) }
    Box(
        modifier
            .clip(cardShape)
            .background(pal.paper)
            .then(
                if (shape.border > 0.dp) Modifier.border(shape.border, pal.line, cardShape)
                else Modifier
            )
            .padding(6.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            // 标题条
            Box(
                Modifier
                    .fillMaxWidth(0.62f)
                    .height(4.dp)
                    .clip(inputShape)
                    .background(pal.ink)
            )
            Spacer(Modifier.height(3.dp));
            // 副标题条
            Box(
                Modifier
                    .fillMaxWidth(0.4f)
                    .height(3.dp)
                    .clip(inputShape)
                    .background(pal.ink3)
            )
            Spacer(Modifier.height(5.dp));
            // 一张卡片 + 强调点
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(width = 20.dp, height = 14.dp)
                        .clip(cardShape)
                        .background(pal.paper2)
                        .then(
                            if (shape.border > 0.dp) Modifier.border(if (shape.border > 1.dp) shape.border else 1.dp, pal.line, cardShape)
                            else Modifier
                        )
                )
                Spacer(Modifier.width(4.dp));
                Box(
                    Modifier
                        .size(if (spec.chrome.filledButtons) 14.dp else 10.dp)
                        .clip(RoundedCornerShape(shape.pill))
                        .background(pal.accent)
                )
            }
        }
    }
}