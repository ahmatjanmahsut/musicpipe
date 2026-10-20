package com.musicpipe.player.ui.tasks

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musicpipe.player.data.remote.dto.TaskDto
import com.musicpipe.player.di.LocalAppContainer
import com.musicpipe.player.ui.components.EmptyBlock
import com.musicpipe.player.ui.components.ErrorBanner
import com.musicpipe.player.ui.components.LoadingBlock
import com.musicpipe.player.ui.theme.DsoColors
import com.musicpipe.player.util.GatewayUtil
import kotlinx.coroutines.delay

/** 下载任务: live view of the router download queue with cancel / clear actions. */
@Composable
fun TasksScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: TasksViewModel = viewModel(factory = TasksViewModel.factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.toast) {
        state.toast?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.consumeToast()
        }
    }
    // Poll while the screen is composed; leaves automatically on navigation.
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            vm.refresh()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                "下载任务", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { vm.refresh() }) {
                Icon(
                    Icons.Filled.Refresh, contentDescription = "刷新",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            TextButton(
                onClick = { vm.cancelActive() },
                enabled = !state.busy && state.tasks.any { GatewayUtil.isActive(it.status) }
            ) { Text("取消进行中") }
            TextButton(
                onClick = { vm.clearFinished() },
                enabled = !state.busy && state.tasks.any { GatewayUtil.isRemovable(it.status) }
            ) { Text("清除已完成") }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        when {
            state.loading && state.tasks.isEmpty() -> LoadingBlock(Modifier.weight(1f))
            state.tasks.isEmpty() -> EmptyBlock(
                state.error ?: "暂无下载任务\n去「网络搜索」或「分享导入」添加歌曲",
                Modifier.weight(1f)
            )
            else -> LazyColumn(Modifier.weight(1f)) {
                if (state.error != null) item(key = "err") { ErrorBanner(state.error!!) }
                items(state.tasks, key = { it.id }) { task ->
                    TaskRow(
                        task = task,
                        busy = state.busy,
                        onCancel = { vm.cancelOne(task.id) },
                        onRemove = { vm.removeOne(task.id) }
                    )
                }
                item(key = "bottom_pad") { Spacer(Modifier.height(90.dp)) }
            }
        }
    }
}

@Composable
private fun TaskRow(task: TaskDto, busy: Boolean, onCancel: () -> Unit, onRemove: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    task.songName?.takeIf { it.isNotBlank() } ?: ("任务 " + task.id),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                // v1.6.0 需求2：失败原因不再直接甩服务端原文（"校验失败: 无法识别的音频文件头"
                // 会让用户以为是文件坏了），而是翻译成"该曲目没有所选音质 → 改成自动/128k 重试"。
                val rawDetail = (if (task.status == "failed") task.error else task.message)
                    ?.takeIf { it.isNotBlank() }
                val detail = if (task.status == "failed") {
                    GatewayUtil.humanizeTaskError(rawDetail)
                } else rawDetail
                val sub = buildList {
                    task.songSource?.takeIf { it.isNotBlank() }?.let { add(it) }
                    add(GatewayUtil.qualityLabel(task.quality))
                    detail?.let { add(it) }
                }.joinToString(" · ")
                Text(
                    sub, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (task.status == "failed") DsoColors.AppRed
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            StatusChip(task.status)
            when {
                GatewayUtil.isActive(task.status) -> IconButton(onClick = onCancel, enabled = !busy) {
                    Icon(
                        Icons.Filled.Cancel, contentDescription = "取消",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                GatewayUtil.isRemovable(task.status) -> IconButton(onClick = onRemove, enabled = !busy) {
                    Icon(
                        Icons.Filled.DeleteOutline, contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> Spacer(Modifier.width(48.dp))
            }
        }
        if (task.status == "downloading" || task.status == "organizing") {
            LinearProgressIndicator(
                progress = { task.progress.coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                color = DsoColors.AppRed,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
private fun StatusChip(status: String?) {
    val fg = when {
        GatewayUtil.isActive(status) -> DsoColors.HighlightBlue
        status == "done" || status == "imported" -> Color(0xFF2E7D32)
        status == "failed" -> DsoColors.AppRed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(10.dp), color = fg.copy(alpha = 0.12f)) {
        Text(
            GatewayUtil.statusLabel(status),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            fontSize = 11.sp,
            color = fg
        )
    }
}
