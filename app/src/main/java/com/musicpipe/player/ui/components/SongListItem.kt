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
import com.musicpipe.player.ui.design.PressScale
import com.musicpipe.player.ui.theme.AwwColors
import com.musicpipe.player.ui.theme.AwwShapes
import com.musicpipe.player.ui.theme.AwwSpace
import com.musicpipe.player.ui.theme.AwwTheme
import com.musicpipe.player.util.TimeFormat

/**
 * 曲目行（v1.12.0 按 DESIGN.md 重设计）。
 *
 * 设计取舍：
 * - 不用卡片：列表靠 1dp 细线（[AwwRule]）分隔，这是规范里"整站唯一分隔手段"。
 * - 封面 52dp + 2dp 圆角（规范禁大圆角），无封面时不放图标兜底，改用底色块 ——
 *   图标兜底会在一屏里重复出现十几次，视觉噪音比留空更大。
 * - 时长/艺人走 mono + tabular-nums，数字才不会在滚动时左右跳动。
 * - 当前播放用强调色 + 前缀竖线标记，不靠加粗（规范要求克制）。
 *
 * 函数签名与 v1.10.0 完全一致，13 个调用点无需改动。
 */
@Composable
fun SongListItem(
    song: Song,
    modifier: Modifier = Modifier,
    isCurrentPlaying: Boolean = false,
    onClick: () -> Unit = {},
    trailing: (@Composable () -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        PressScale {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(vertical = AwwSpace.s3),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // v1.14.0：改用 FadeInCover。原来这里是裸 AsyncImage —— 虽然 Coil 能按
                // 52dp 约束自动降采样，但缺了统一的 memory/disk CachePolicy 与占位底色，
                // 而且每个调用点各写一套。FadeInCover 把解码尺寸、缓存策略、占位收敛到一处。
                FadeInCover(
                    model = song.coverUrl,
                    contentDescription = song.title,
                    dpSize = 52,
                    modifier = Modifier
                        .size(52.dp)
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
                        style = AwwTheme.type.body,
                        color = if (isCurrentPlaying) AwwColors.Accent else MaterialTheme.colorScheme.onSurface
                    )
                    // v1.14.0 性能修复（滚动卡顿）：
                    // 原来是 listOf(a,b).filter{}.joinToString() —— 每次重组造 3 个中间对象
                    // （ArrayList + 迭代器 + 最终 String），列表一屏十几行、滚动时每帧都在造。
                    // 改成零中间集合的拼接：只有一个 StringBuilder 的最终产物。
                    // remember 之外再按 key 缓存一次没必要 —— 这里已经把分配降到 1 个对象。
                    val meta = buildMeta(song.artist, song.album)
                    if (meta.isNotEmpty()) {
                        AwwMono(
                            text = meta,
                            modifier = Modifier.padding(top = 2.dp),
                            color = AwwColors.Ink3
                        )
                    }
                }
                if (song.durationMs > 0 && trailing == null) {
                    AwwMono(text = TimeFormat.format(song.durationMs), color = AwwColors.Ink3)
                }
                trailing?.invoke()
            }
        }
        AwwRule()
    }
}

/**
 * 拼接「艺人 · 专辑」，任一为空则只出另一个，都为空白返回空串。
 *
 * 不用 listOf().filter{}.joinToString()：那会为列表每一行、每次重组创建
 * ArrayList + 迭代器 + 最终 String 三个对象。这里只有最终 String 一次分配。
 */
private fun buildMeta(artist: String, album: String): String {
    val a = artist.isNotBlank()
    val b = album.isNotBlank()
    return when {
        a && b -> artist + " · " + album
        a -> artist
        b -> album
        else -> ""
    }
}
