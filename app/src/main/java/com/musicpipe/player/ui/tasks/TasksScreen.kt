package com.musicpipe.player.ui.tasks

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musicpipe.player.data.remote.dto.TaskDto
import com.musicpipe.player.di.LocalAppContainer
import com.musicpipe.player.ui.components.ErrorBanner
import com.musicpipe.player.ui.components.LoadingBlock
import com.musicpipe.player.ui.design.AwwEmpty
import com.musicpipe.player.ui.design.AwwLabel
import com.musicpipe.player.ui.design.AwwMono
import com.musicpipe.player.ui.design.AwwPageHeader
import com.musicpipe.player.ui.design.AwwRule
import com.musicpipe.player.ui.design.HairlineProgress
import com.musicpipe.player.ui.theme.AwwColors
import com.musicpipe.player.ui.theme.AwwSpace
import com.musicpipe.player.ui.theme.AwwTheme
import com.musicpipe.player.util.GatewayUtil
import com.musicpipe.player.util.metaText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 下载任务（v1.12.0 按 DESIGN.md 重构）。
 *
 * 视觉：行 = mono 编号 + 曲名（body）+ mono 细节行 + 右侧状态文本 + 操作；
 * 进度不再是 M3 粗进度条，改为 1dp 细线 [HairlineProgress]，与整站细线语言一致。
 *
 * 业务逻辑与 v1.10.0 完全一致：
 * - 轮询保持 **生命周期感知**（LifecycleResumeEffect，进 STOPPED 立即取消），
 *   否则按 Home 键回桌面期间仍在每 3 秒发一次 HTTP。
 * - 失败原因仍走 GatewayUtil.humanizeTaskError（v1.6.0 需求2 的人话翻译）。
 */
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

    val pollScope = rememberCoroutineScope()
    LifecycleResumeEffect(Unit) {
        val job = pollScope.launch {
            while (true) {
                delay(3000)
                vm.refresh()
            }
        }
        onPauseOrDispose { job.cancel() }
    }

    val activeCount = state.tasks.count { GatewayUtil.isActive(it.status) }
    val removableCount = state.tasks.count { GatewayUtil.isRemovable(it.status) }

    Column(Modifier.fillMaxSize()) {
        AwwPageHeader(
            index = "07",
            label = "TASKS",
            title = "下载任务",
            meta = state.tasks.size.toString().padStart(3, '0') + " 个任务 · " +
                activeCount.toString().padStart(2, '0') + " 进行中",
            onBack = onBack
        )

        // 批量操作行：只保留两个真正有用的（取消进行中 / 清除已完成），
        // 不再另设「刷新」——下面每次操作都会拉一次，下拉也会重进页面拉一次。
        Row(
            Modifier.fillMaxWidth().padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s2),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AwwLabel(
                text = "取消进行中",
                color = if (!state.busy && activeCount > 0) AwwColors.Accent else AwwColors.Ink3,
                modifier = Modifier
                    .clickable(enabled = !state.busy && activeCount > 0) { vm.cancelActive() }
                    .padding(vertical = AwwSpace.s2)
            )
            Spacer(Modifier.width(AwwSpace.s6))
            AwwLabel(
                text = "清除已完成",
                color = if (!state.busy && removableCount > 0) MaterialTheme.colorScheme.onSurface else AwwColors.Ink3,
                modifier = Modifier
                    .clickable(enabled = !state.busy && removableCount > 0) { vm.clearFinished() }
                    .padding(vertical = AwwSpace.s2)
            )
        }
        AwwRule()

        when {
            state.loading && state.tasks.isEmpty() -> LoadingBlock(Modifier.weight(1f))
            state.tasks.isEmpty() -> AwwEmpty(
                index = "000",
                title = "暂无下载任务",
                hint = state.error ?: "去「网络搜索」或「分享导入」添加歌曲",
                modifier = Modifier.weight(1f)
            )
            else -> LazyColumn(Modifier.weight(1f)) {
                if (state.error != null) item(key = "err", contentType = "error") {
                    ErrorBanner(state.error!!)
                }
                // v1.14.0 性能修复（滚动卡顿）：原来是 items(state.tasks) { task ->
                // ... index = state.tasks.indexOf(task) + 1 }。indexOf 是线性查找，
                // 每行一次 → 整个列表 O(n²)；而列表在下载进行中每 500ms 就会因进度更新重组，
                // n=50 的任务列表每帧就要做 1250 次引用比较。改成 itemsIndexed，序号直接来自下标。
                itemsIndexed(state.tasks, key = { _, it -> it.id }, contentType = { _, _ -> "task" }) { i, task ->
                    TaskRow(
                        task = task,
                        index = i + 1,
                        busy = state.busy,
                        onCancel = { vm.cancelOne(task.id) },
                        onRemove = { vm.removeOne(task.id) }
                    )
                    AwwRule()
                }
                item(key = "bottom_pad", contentType = "spacer") {
                    Spacer(Modifier.height(AwwSpace.s24))
                }
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: TaskDto,
    index: Int,
    busy: Boolean,
    onCancel: () -> Unit,
    onRemove: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s3)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AwwMono(
                text = index.toString().padStart(2, '0'),
                color = AwwColors.Ink3,
                modifier = Modifier.width(AwwSpace.s8)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    task.songName?.takeIf { it.isNotBlank() } ?: ("任务 " + task.id),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = AwwTheme.type.body,
                    color = MaterialTheme.colorScheme.onSurface
                )
                // v1.6.0 需求2：失败原因翻译成人话（"该曲目没有所选音质 → 改自动/128k 重试"），
                // 不直接甩服务端原文，否则用户会以为文件坏了。
                val rawDetail = (if (task.status == "failed") task.error else task.message)
                    ?.takeIf { it.isNotBlank() }
                val detail = if (task.status == "failed") {
                    GatewayUtil.humanizeTaskError(rawDetail)
                } else rawDetail
                val sub = metaText {
                    add(task.songSource)
                    add(GatewayUtil.qualityLabel(task.quality))
                    add(detail)
                }
                Text(
                    sub, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    style = AwwTheme.type.bodySmall,
                    color = if (task.status == "failed") AwwColors.Accent else AwwColors.Ink3
                )
            }
            Spacer(Modifier.width(AwwSpace.s2))
            // 状态用纯文字 + 颜色，不用彩色药丸底（规范禁大圆角标签块）
            Text(
                GatewayUtil.statusLabel(task.status),
                style = AwwTheme.type.label,
                color = statusColor(task.status)
            )
            when {
                GatewayUtil.isActive(task.status) -> IconButton(onClick = onCancel, enabled = !busy) {
                    Icon(
                        Icons.Filled.Cancel, contentDescription = "取消",
                        tint = AwwColors.Ink3, modifier = Modifier.width(18.dp)
                    )
                }
                GatewayUtil.isRemovable(task.status) -> IconButton(onClick = onRemove, enabled = !busy) {
                    Icon(
                        Icons.Filled.DeleteOutline, contentDescription = "删除",
                        tint = AwwColors.Ink3, modifier = Modifier.width(18.dp)
                    )
                }
                else -> Spacer(Modifier.width(AwwSpace.s12))
            }
        }
        if (task.status == "downloading" || task.status == "organizing") {
            Spacer(Modifier.height(AwwSpace.s2))
            HairlineProgress(
                progress = task.progress.coerceIn(0, 100) / 100f,
                modifier = Modifier.fillMaxWidth().padding(start = AwwSpace.s8)
            )
        }
    }
}

/** 状态只有四档颜色：进行中=强调色 / 完成=前景 / 失败=强调色 / 其它=弱文。 */
@Composable
private fun statusColor(status: String?): Color = when {
    GatewayUtil.isActive(status) -> AwwColors.Accent
    status == "done" || status == "imported" -> MaterialTheme.colorScheme.onSurface
    status == "failed" -> AwwColors.Accent
    else -> AwwColors.Ink3
}
