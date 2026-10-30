package com.musicpipe.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musicpipe.player.data.image.FadeInCover
import com.musicpipe.player.data.model.Song
import com.musicpipe.player.ui.design.AwwMono
import com.musicpipe.player.ui.design.AwwRule
import com.musicpipe.player.ui.design.HairlineProgress
import com.musicpipe.player.ui.theme.AwwColors
import com.musicpipe.player.ui.theme.AwwShapes
import com.musicpipe.player.ui.theme.AwwSpace
import com.musicpipe.player.ui.theme.AwwTheme

/**
 * 迷你播放条（v1.12.0 按 DESIGN.md 重设计）。
 *
 * 取舍：
 * - 不再用 LinearProgressIndicator：M3 那个自带 track/圆角/动画，与"1dp 细线"的
 *   整站语言冲突，改用 [HairlineProgress]（1dp track + 2dp 头部方点）。
 * - 不用"毛玻璃 + 大圆角"的胶囊（规范负面清单第 2 条明令禁止）。
 * - 播放按钮从红色 IconButton 改为无填充图标 + 强调色描边方块，面积 < 5%。
 * - 顶部 1dp 细线把它与内容分开，替代阴影。
 *
 * 函数签名与 v1.10.0 完全一致，调用点无需改动。
 */
@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    progress: Float,
    onPlayPauseClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        AwwRule()
        HairlineProgress(
            progress = progress.coerceIn(0f, 1f),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AwwColors.Paper2)
                .clickable(onClick = onClick)
                .padding(horizontal = AwwSpace.s4, vertical = AwwSpace.s2),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // v1.14.0：改用 FadeInCover。这里尤其重要 —— 迷你条会因为 500ms 的播放
            // 进度心跳持续重组，裸 AsyncImage 每次重组都新建 ImageRequest；
            // FadeInCover 内部用 remember(model, dpSize, context) 把请求钉住，
            // 心跳重组不再重复构造请求、也不会重新触发解码。
            FadeInCover(
                model = song.coverUrl,
                contentDescription = song.title,
                dpSize = 40,
                modifier = Modifier
                    .size(40.dp)
                    .clip(AwwShapes.Image)
                    .background(AwwColors.Paper2),
                contentScale = ContentScale.Crop
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = AwwSpace.s3)
            ) {
                Text(
                    text = song.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = AwwTheme.type.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (song.artist.isNotBlank()) {
                    AwwMono(text = song.artist, color = AwwColors.Ink3)
                }
            }
            IconButton(onClick = onPlayPauseClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = AwwColors.Accent,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
