package com.musicpipe.player.ui.player

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicpipe.player.data.image.FadeInCover
import com.musicpipe.player.data.model.Song
import com.musicpipe.player.di.LocalAppContainer
import com.musicpipe.player.ui.design.AwwLabel
import com.musicpipe.player.ui.design.AwwMono
import com.musicpipe.player.ui.design.AwwRule
import com.musicpipe.player.ui.design.ClipReveal
import com.musicpipe.player.ui.design.MaskReveal
import com.musicpipe.player.ui.design.PressScale
import com.musicpipe.player.ui.theme.AwwColors
import com.musicpipe.player.ui.theme.AwwShapes
import com.musicpipe.player.ui.theme.AwwSpace
import com.musicpipe.player.ui.theme.AwwTheme
import com.musicpipe.player.ui.theme.LocalReduceMotion
import com.musicpipe.player.util.TimeFormat
import com.musicpipe.player.util.metaText
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 播放页（v1.12.0 按 DESIGN.md 重构）。
 *
 * 设计取舍：
 * - 去掉 DsoMusic 的圆形 CD 盘：圆形 + 大投影是规范负面清单里的"卡片套卡片/大圆角"语汇，
 *   编辑式排版应该让**方形图片出血**、让标题成为视觉主体。这里改成 1:1 方图、贴左、
 *   带 [ClipReveal] 入场。
 * - 标题用 display 流式字号（clamp 到 30..64sp），艺人/专辑走 mono 大写小标签。
 * - 传输控件不再用"72dp 红色实心圆"：改成图标本身为强调色、无填充容器，
 *   把唯一的大面积强调色留给播放主按钮的**描边**。
 * - 进度条保留 M3 Slider（手势逻辑成熟，重写自定义容易在真机拖拽出问题），
 *   但把颜色全部换成 token，视觉退化为 1dp 细线 + 强调色头部。
 *
 * 性能约定（与 v1.10.0 一致，勿破坏）：
 * - 本页只订阅低频字段（曲目/是否播放/时长），500ms 的 positionMs 由 [SeekBar] 单独订阅，
 *   否则整页（封面解码、收藏查询、控件树）每秒重组两次。
 * - 收藏 flow 必须 remember(song.id)，否则每次重组都新建一条 Room 查询流。
 */
@Composable
fun PlayerScreen(onBack: () -> Unit, onOpenLyrics: () -> Unit) {
    val container = LocalAppContainer.current
    val header by remember {
        container.playerRepository.state
            .map { TrackHeader(it.currentSong, it.isPlaying, it.durationMs) }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = TrackHeader(null, false, 0L))
    val song = header.song
    val scope = rememberCoroutineScope()

    val favoriteFlow = remember(song?.id) {
        song?.let { s -> container.favoriteRepository.observeIsFavorite(s.id) } ?: flowOf(false)
    }
    val isFav by favoriteFlow.collectAsStateWithLifecycle(initialValue = false)
    val config = LocalConfiguration.current
    val wideCar = com.musicpipe.player.ui.theme.ResponsiveLayout.isWideCar(config.screenWidthDp, config.screenHeightDp)

    if (wideCar) {
        LandscapePlayer(
            song = song,
            isPlaying = header.isPlaying,
            durationMs = header.durationMs,
            isFavorite = isFav,
            onBack = onBack,
            onOpenLyrics = onOpenLyrics,
            onFavorite = { song?.let { track -> scope.launch { container.favoriteRepository.toggle(track, isFav) } } }
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = AwwSpace.s6)
    ) {
        // 顶栏：仅一个 ‹ 返回 + 右侧收藏。标题不放这里 —— 它属于下方的大排版。
        Row(
            Modifier.fillMaxWidth().padding(top = AwwSpace.s2),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.KeyboardArrowDown, "收起播放页", tint = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.weight(1f))
            AwwLabel(text = "正在播放", color = AwwColors.Ink3)
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { song?.let { s -> scope.launch { container.favoriteRepository.toggle(s, isFav) } } },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isFav) "取消收藏" else "收藏",
                    tint = if (isFav) AwwColors.Accent else AwwColors.Ink3,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.height(AwwSpace.s6))

        // 方图贴左、1:1，按屏宽收边（不做居中卡片）。
        SpinningRecord(model = song?.coverUrl, title = song?.title, playing = header.isPlaying)
        Spacer(Modifier.height(AwwSpace.s6))

        MaskReveal {
            Text(
                text = song?.title ?: "未在播放",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = AwwTheme.type.h3,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (song != null) {
            Text(
                text = metaText(" · ") { add(song.artist); add(song.album) },
                style = AwwTheme.type.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = AwwSpace.s2),
                color = AwwColors.Ink3
            )
        }

        Spacer(Modifier.weight(1f))

        SeekBar(durationMs = header.durationMs)

        Spacer(Modifier.height(AwwSpace.s4))

        // 传输控件：‹‹ / ▮▮ / ›› 图标为强调色、无容器填充；只有播放主钮加 1dp 描边。
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = { container.playerRepository.previous() },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Filled.SkipPrevious, "上一首",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp)
                )
            }
            PressScale {
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(AwwShapes.Pill)
                        .background(AwwColors.Accent),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = { container.playerRepository.togglePlayPause() }) {
                        Icon(
                            imageVector = if (header.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (header.isPlaying) "暂停" else "播放",
                            // v1.13.0：强调色之上的图标/文字必须用 OnAccent —— 各风格的强调色明暗不同
                    //（Material 深色模式是浅紫、shadcn 是纯白），写死 Paper 会出现"浅底浅字"。
                    tint = AwwColors.OnAccent,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
            IconButton(
                onClick = { container.playerRepository.next() },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Filled.SkipNext, "下一首",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // 歌词入口：整行的细线 + 大写标签 + 右箭头，替代原来的圆角胶囊按钮。
        Spacer(Modifier.height(AwwSpace.s4))
        AwwRule()
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenLyrics)
                .padding(vertical = AwwSpace.s4),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AwwLabel(text = "查看歌词", color = AwwColors.Ink2)
            Spacer(Modifier.weight(1f))
            AwwMono(text = "→", color = AwwColors.Ink3)
        }
        Spacer(Modifier.height(AwwSpace.s4))
    }
}

@Composable
private fun LandscapePlayer(
    song: Song?,
    isPlaying: Boolean,
    durationMs: Long,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onOpenLyrics: () -> Unit,
    onFavorite: () -> Unit
) {
    val container = LocalAppContainer.current
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = AwwSpace.s6)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.KeyboardArrowDown, "收起播放页", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text("正在播放", style = AwwTheme.type.bodySmall, color = AwwColors.Ink3)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onFavorite, modifier = Modifier.size(48.dp)) {
                Icon(if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    if (isFavorite) "取消收藏" else "收藏",
                    tint = if (isFavorite) AwwColors.Accent else AwwColors.Ink3)
            }
        }
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(0.85f).padding(end = AwwSpace.s8), contentAlignment = Alignment.Center) {
                SpinningRecord(model = song?.coverUrl, title = song?.title, playing = isPlaying)
            }
            Column(Modifier.weight(1.15f)) {
                Text(song?.title ?: "未在播放", style = AwwTheme.type.h3,
                    color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(metaText(" · ") { song?.let { add(it.artist); add(it.album) } },
                    style = AwwTheme.type.bodySmall, color = AwwColors.Ink3,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(AwwSpace.s3))
                SeekBar(durationMs = durationMs)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { container.playerRepository.previous() }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Filled.SkipPrevious, "上一首", modifier = Modifier.size(32.dp))
                    }
                    PressScale {
                        Box(Modifier.size(64.dp).clip(AwwShapes.Pill).background(AwwColors.Accent), contentAlignment = Alignment.Center) {
                            IconButton(onClick = { container.playerRepository.togglePlayPause() }) {
                                Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    if (isPlaying) "暂停" else "播放", tint = AwwColors.OnAccent, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                    IconButton(onClick = { container.playerRepository.next() }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Filled.SkipNext, "下一首", modifier = Modifier.size(32.dp))
                    }
                }
                Row(Modifier.fillMaxWidth().clickable(onClick = onOpenLyrics).padding(vertical = AwwSpace.s2),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lyrics, null, tint = AwwColors.Ink2)
                    Spacer(Modifier.width(AwwSpace.s2))
                    Text("查看歌词", style = AwwTheme.type.bodySmall, color = AwwColors.Ink2)
                }
            }
        }
    }
}

/**
 * 播放页"低频头部状态"（v1.10.0 引入，v1.12.0 保留）：
 * 只装当前曲目、是否在播、总时长，配合 distinctUntilChanged 让整页不被 500ms 进度刷新带动。
 */
private data class TrackHeader(
    val song: Song?,
    val isPlaying: Boolean,
    val durationMs: Long
)

/**
 * 进度条独立订阅 positionMs（v1.10.0 引入，v1.12.0 保留配色 token 化）。
 * 拖拽期间用本地 [seekPos] 覆盖，松手 seekTo 后清空覆盖，避免手指未松就被上游位置拉回。
 */
@Composable
private fun SeekBar(durationMs: Long) {
    val container = LocalAppContainer.current
    val positionMs by remember {
        container.playerRepository.state
            .map { it.positionMs }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = 0L)

    var seeking by remember { mutableStateOf(false) }
    var seekPos by remember { mutableFloatStateOf(0f) }
    val duration = durationMs.coerceAtLeast(1L)
    val pos = if (seeking) seekPos.toLong() else positionMs

    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = (pos.toFloat() / duration.toFloat()).coerceIn(0f, 1f),
            onValueChange = { seeking = true; seekPos = it * duration.toFloat() },
            onValueChangeFinished = {
                container.playerRepository.seekTo(seekPos.toLong())
                seeking = false
            },
            colors = SliderDefaults.colors(
                thumbColor = AwwColors.Accent,
                activeTrackColor = MaterialTheme.colorScheme.onSurface,
                inactiveTrackColor = AwwColors.Line
            )
        )
        Row(Modifier.fillMaxWidth().padding(top = AwwSpace.s1)) {
            AwwMono(text = TimeFormat.format(pos), color = AwwColors.Ink3)
            Spacer(Modifier.weight(1f))
            AwwMono(text = TimeFormat.format(durationMs), color = AwwColors.Ink3)
        }
    }
}

@Composable
private fun SpinningRecord(model: String?, title: String?, playing: Boolean) {
    val reduceMotion = LocalReduceMotion.current
    val rotation = androidx.compose.animation.core.Animatable(0f)
    val angle = remember { rotation }
    androidx.compose.runtime.LaunchedEffect(playing, reduceMotion) {
        if (playing && !reduceMotion) {
            while (true) {
                angle.animateTo(
                    targetValue = angle.value + 360f,
                    animationSpec = tween(24000, easing = androidx.compose.animation.core.LinearEasing)
                )
            }
        } else angle.stop()
    }
    Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxSize().clip(CircleShape).background(AwwColors.Paper2)
                .border(AwwSpace.s4, AwwColors.Line, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            FadeInCover(
                model = model, contentDescription = title,
                dpSize = LocalConfiguration.current.screenWidthDp,
                modifier = Modifier.fillMaxSize(0.78f).clip(CircleShape).graphicsLayer { rotationZ = angle.value },
                contentScale = ContentScale.Crop
            )
            Box(Modifier.fillMaxSize(0.16f).clip(CircleShape).background(AwwColors.Paper2))
            Box(Modifier.fillMaxSize(0.055f).clip(CircleShape).background(AwwColors.Paper))
        }
    }
}
