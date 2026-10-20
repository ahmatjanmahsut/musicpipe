package com.musicpipe.player.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.musicpipe.player.di.LocalAppContainer
import com.musicpipe.player.ui.theme.DsoColors
import com.musicpipe.player.util.TimeFormat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Full player page, DsoMusic style (docs/03): circular cover on #80FFFFFF plate,
 * red transport controls, heart favorite toggle (requirement 4), lyrics entry.
 */
@Composable
fun PlayerScreen(onBack: () -> Unit, onOpenLyrics: () -> Unit) {
    val container = LocalAppContainer.current
    val playerState by container.playerRepository.state.collectAsStateWithLifecycle()
    val song = playerState.currentSong
    val scope = rememberCoroutineScope()

    val isFav by (song?.let { s ->
        container.favoriteRepository.observeIsFavorite(s.id)
    } ?: flowOf(false)).collectAsStateWithLifecycle(initialValue = false)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onSurface)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    song?.title ?: "未在播放", maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center
                )
                if (song != null) {
                    Text(
                        song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = {
                song?.let { s -> scope.launch { container.favoriteRepository.toggle(s, isFav) } }
            }) {
                Icon(
                    if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    if (isFav) "取消收藏" else "收藏",
                    tint = if (isFav) DsoColors.AppRed else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(290.dp).clip(CircleShape).background(DsoColors.CdPlateBg),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(250.dp).clip(CircleShape).background(DsoColors.CoverPlaceholder)) {
                    if (song?.coverUrl != null) {
                        AsyncImage(
                            model = song.coverUrl, contentDescription = song.title,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(64.dp))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        var seeking by remember { mutableStateOf(false) }
        var seekPos by remember { mutableFloatStateOf(0f) }
        val duration = playerState.durationMs.coerceAtLeast(1L)
        val pos = if (seeking) seekPos.toLong() else playerState.positionMs
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Slider(
                value = (pos.toFloat() / duration.toFloat()).coerceIn(0f, 1f),
                onValueChange = { seeking = true; seekPos = it * duration.toFloat() },
                onValueChangeFinished = {
                    container.playerRepository.seekTo(seekPos.toLong())
                    seeking = false
                },
                colors = SliderDefaults.colors(
                    thumbColor = DsoColors.AppRed,
                    activeTrackColor = DsoColors.AppRed,
                    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
            Row(Modifier.fillMaxWidth()) {
                Text(TimeFormat.format(pos), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(TimeFormat.format(playerState.durationMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = { container.playerRepository.previous() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.SkipPrevious, "上一首", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(36.dp))
            }
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(DsoColors.AppRed),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = { container.playerRepository.togglePlayPause() }) {
                    Icon(
                        if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (playerState.isPlaying) "暂停" else "播放",
                        tint = DsoColors.PageWhite, modifier = Modifier.size(40.dp)
                    )
                }
            }
            IconButton(onClick = { container.playerRepository.next() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.SkipNext, "下一首", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(36.dp))
            }
        }

        Box(Modifier.fillMaxWidth().padding(bottom = 28.dp), contentAlignment = Alignment.Center) {
            Row(
                Modifier.clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onOpenLyrics)
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("查看歌词", color = DsoColors.HighlightBlue, fontSize = 14.sp)
            }
        }
    }
}
