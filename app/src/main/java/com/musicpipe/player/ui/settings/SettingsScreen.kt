package com.musicpipe.player.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musicpipe.player.ui.components.AppOutlinedTextField
import com.musicpipe.player.BuildConfig
import com.musicpipe.player.di.LocalAppContainer
import com.musicpipe.player.ui.nav.NavGraphSpec
import com.musicpipe.player.ui.theme.DsoColors
import com.musicpipe.player.ui.update.UpdateDialog
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator

/**
 * Settings page. Admin-only 「用户管理」 entry comes from NavGraphSpec.settingsEntries(role)
 * — for role=user the entry does not exist at all (requirement 1: 彻底隐藏, not disabled).
 */
@Composable
fun SettingsScreen(onNavigate: (String) -> Unit) {
    val container = LocalAppContainer.current
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
    val settings by vm.settings.collectAsStateWithLifecycle()
    val user by container.authRepository.currentUser.collectAsStateWithLifecycle()
    val systemInfo by vm.systemInfo.collectAsStateWithLifecycle()
    val systemBusy by vm.systemBusy.collectAsStateWithLifecycle()
    val netTest by vm.netTest.collectAsStateWithLifecycle()

    var showUpdate by remember { mutableStateOf(false) }
    var showLogout by remember { mutableStateOf(false) }
    var editServer by remember { mutableStateOf(false) }
    var serverDraft by remember { mutableStateOf("") }

    if (showUpdate) { UpdateDialog(onDismiss = { showUpdate = false }) }

    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            title = { Text("退出登录") },
            text = { Text("确定退出当前账号吗？本地历史与收藏会保留。") },
            confirmButton = {
                TextButton(onClick = { vm.logout(); showLogout = false }) {
                    Text("退出", color = DsoColors.AppRed)
                }
            },
            dismissButton = { TextButton(onClick = { showLogout = false }) { Text("取消") } }
        )
    }

    if (editServer) {
        AlertDialog(
            onDismissRequest = { editServer = false },
            title = { Text("服务器地址") },
            text = {
                AppOutlinedTextField(
                    value = serverDraft, onValueChange = { serverDraft = it },
                    label = { Text("MusicPipe 服务地址（含端口）") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.setServerUrl(serverDraft); editServer = false }) {
                    Text("保存", color = DsoColors.AppRed)
                }
            },
            dismissButton = { TextButton(onClick = { editServer = false }) { Text("取消") } }
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
    ) {
        Text(
            "设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )

        // account section
        SectionTitle("账号")
        EntryRow(user?.username ?: "-", "当前用户（${if (user?.isAdmin == true) "管理员" else "普通用户"}）")
        // admin-only entries — absent entirely for role=user
        NavGraphSpec.settingsEntries(user?.role).forEach { entry ->
            EntryRow(entry.label, "管理用户：增删改查与角色分配", onClick = { onNavigate(entry.route) })
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SectionTitle("服务器")
        EntryRow("MusicPipe 服务", settings.serverUrl, onClick = {
            serverDraft = settings.serverUrl; editServer = true
        })
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // v1.6.0 需求2：用户要求「设置中的刷新状态改成与服务器的延迟和网速测试」。
        // v1.5.0 曾把网络测试**另起一块**加在下面，但「刷新状态」这一行还留着，
        // 两处功能重叠 —— 用户点"刷新状态"以为会测速，结果只是读统计数字。
        // 现在合并成一块：网络测试面板（延迟/带宽）在上，顺手带回的服务器统计在下，
        // 一次点击同时给出"快不快"和"库里有多少"，不再有第二个"刷新"入口。
        SectionTitle("网络测试")
        NetworkTestPanel(state = netTest, onRun = { vm.runNetworkTest() })
        val counts = systemInfo?.counts
        EntryRow("曲库歌曲", counts?.tracks?.toString() ?: "-")
        EntryRow("进行中任务", counts?.queue?.toString() ?: "-")
        EntryRow("已入库", counts?.imported?.toString() ?: "-")
        EntryRow(
            "刷新状态", if (systemBusy) "请求中…" else "测延迟/网速并更新统计",
            onClick = {
                // 刷新状态 = 网络测试 + 统计刷新，一次点全都要（旧行为只读统计）
                vm.runNetworkTest()
                vm.refreshSystem()
            }
        )
        EntryRow(
            "触发 Navidrome 扫描", "歌曲入库后同步媒体库",
            onClick = { vm.triggerScan() }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SectionTitle("外观")
        listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (mode, label) ->
            Row(
                Modifier.fillMaxWidth().clickable { vm.setThemeMode(mode) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                if (settings.themeMode == mode) Text("✓", color = DsoColors.AppRed, fontWeight = FontWeight.Bold)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        SectionTitle("关于")
        EntryRow("检查更新", "当前版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", onClick = { showUpdate = true })
        EntryRow("退出登录", "", onClick = { showLogout = true }, valueColor = DsoColors.AppRed)
        Spacer(Modifier.height(90.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp)
    )
}

@Composable
private fun EntryRow(
    title: String,
    subtitle: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = valueColor)
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
                )
            }
        }
        if (onClick != null) Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleLarge)
    }
}
/**
 * v1.5.0 网络测试面板：延迟 + 下载带宽。
 *
 * 展示原则：
 *   - 未测过时只给一句"点下面的按钮"，不预先摆一排 "—"，避免看起来像功能坏了。
 *   - 测完把**每一次采样**都列出来（中位数 + 每次耗时），用户能看出是不是偶发抖动。
 *   - 带宽用实测读到的字节数算，并注明"只下载前 2MB"，让数字可被怀疑也可被理解。
 */
@Composable
private fun NetworkTestPanel(state: SettingsViewModel.NetworkTest, onRun: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        val rtt = state.rtt
        val speed = state.speed

        if (state.running) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = DsoColors.AppRed)
                Text("测试中…", Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodyMedium)
            }
        } else if (rtt == null && speed == null && state.error == null) {
            Text(
                "测量与服务器的延迟和下载速度，判断瓶颈在本地网络还是服务端。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            rtt?.let {
                MetricRow("服务器延迟", it.medianMs.toString() + " ms", qualityColor(it.medianMs))
                Text(
                    "每次：" + com.musicpipe.player.data.remote.formatSamples(it.samplesMs) +
                        "　最快 " + it.minMs + "ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            speed?.let {
                MetricRow("下载速度", it.humanReadable, qualityColorBySpeed(it.bytesPerSecond))
                Text(
                    "读取 " + com.musicpipe.player.data.remote.humanBytes(it.bytesRead) +
                        " / 用时 " + it.elapsedMs + " ms（仅前 2MB）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            state.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = DsoColors.AppRed)
            }
        }

        Button(
            onClick = onRun,
            enabled = !state.running,
            modifier = Modifier.padding(top = 8.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = DsoColors.AppRed)
        ) {
            Text(if (state.testedAt == null) "开始测试" else "重新测试")
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

/** 延迟分档：<80ms 正常(品牌红) / <200ms 一般(橙) / 其余偏慢(浅红)。LAN 应落在第一档。 */
private fun qualityColor(ms: Long): androidx.compose.ui.graphics.Color = when {
    ms < 80 -> DsoColors.AppRed
    ms < 200 -> androidx.compose.ui.graphics.Color(0xFFE6A23C)
    else -> androidx.compose.ui.graphics.Color(0xFFF56C6C)
}

/** 带宽分档：>=2MB/s 正常 / >=500KB/s 一般 / 其余偏慢。 */
private fun qualityColorBySpeed(bps: Double): androidx.compose.ui.graphics.Color = when {
    bps >= 2.0 * 1024 * 1024 -> DsoColors.AppRed
    bps >= 500 * 1024 -> androidx.compose.ui.graphics.Color(0xFFE6A23C)
    else -> androidx.compose.ui.graphics.Color(0xFFF56C6C)
}
