package com.musicpipe.player.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musicpipe.player.BuildConfig
import com.musicpipe.player.di.LocalAppContainer
import com.musicpipe.player.ui.design.AwwLabel
import com.musicpipe.player.ui.design.AwwMono
import com.musicpipe.player.ui.design.AwwPageHeader
import com.musicpipe.player.ui.design.AwwRule
import com.musicpipe.player.ui.nav.NavGraphSpec
import com.musicpipe.player.ui.nav.Routes
import com.musicpipe.player.ui.theme.AppStyle
import com.musicpipe.player.ui.theme.AwwColors
import com.musicpipe.player.ui.theme.AwwShapes
import com.musicpipe.player.ui.theme.AwwSpace
import com.musicpipe.player.ui.theme.AwwTheme
import com.musicpipe.player.ui.update.UpdateDialog

/**
 * 设置页（v1.12.0 按 DESIGN.md 重构）。
 *
 * 结构：页眉（[AwwPageHeader]）→ 若干分组，每组 = mono 小节编号 + 若干行 + 1dp 细线。
 * 行不再是"标题 + 副标题 + ›"的 M3 样式，而是"uppercase 小标签 + 值（mono 或 body）"，
 * 右侧只在可点时给一个箭头。
 *
 * 需求1 的 admin-only「用户管理」入口仍来自 [NavGraphSpec.settingsEntries]：
 * role=user 时该条**根本不存在**（不是置灰）。
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
    // v1.16.0：音效与车载歌词的当前状态（预设名 / 输出设备 / 是否正在推送）。
    val eqUi by container.eqRepository.state.collectAsStateWithLifecycle()
    val carUi by container.carLyricsRepository.state.collectAsStateWithLifecycle()

    var showUpdate by remember { mutableStateOf(false) }
    var showLogout by remember { mutableStateOf(false) }
    var editServer by remember { mutableStateOf(false) }
    var serverDraft by remember { mutableStateOf("") }

    if (showUpdate) { UpdateDialog(onDismiss = { showUpdate = false }) }

    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            containerColor = MaterialTheme.colorScheme.background,
            shape = AwwShapes.Square,
            title = { Text("退出登录", style = AwwTheme.type.h3) },
            text = {
                Text(
                    "确定退出当前账号吗？本地历史与收藏会保留。",
                    style = AwwTheme.type.bodySmall,
                    color = AwwColors.Ink2
                )
            },
            confirmButton = {
                AwwLabel(
                    text = "退出", color = AwwColors.Accent,
                    modifier = Modifier.clickable { vm.logout(); showLogout = false }.padding(AwwSpace.s3)
                )
            },
            dismissButton = {
                AwwLabel(
                    text = "取消", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { showLogout = false }.padding(AwwSpace.s3)
                )
            }
        )
    }

    if (editServer) {
        AlertDialog(
            onDismissRequest = { editServer = false },
            containerColor = MaterialTheme.colorScheme.background,
            shape = AwwShapes.Square,
            title = { Text("服务器地址", style = AwwTheme.type.h3) },
            text = {
                Column {
                    AwwLabel(text = "MTmusic 服务地址（含端口）", color = AwwColors.Ink3)
                    Spacer(Modifier.height(AwwSpace.s2))
                    Box(Modifier.fillMaxWidth().padding(vertical = AwwSpace.s2)) {
                        BasicTextField(
                            value = serverDraft,
                            onValueChange = { serverDraft = it },
                            singleLine = true,
                            textStyle = AwwTheme.type.body.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(AwwColors.Accent),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    AwwRule()
                }
            },
            confirmButton = {
                AwwLabel(
                    text = "保存", color = AwwColors.Accent,
                    modifier = Modifier.clickable { vm.setServerUrl(serverDraft); editServer = false }.padding(AwwSpace.s3)
                )
            },
            dismissButton = {
                AwwLabel(
                    text = "取消", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { editServer = false }.padding(AwwSpace.s3)
                )
            }
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AwwPageHeader(
            index = "06",
            label = "SETTINGS",
            title = "设置",
            meta = "v" + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")"
        )

        // ---- 账号 ----
        SectionTitle(number = "01", text = "账号")
        SettingRow(label = "当前用户", value = user?.username ?: "—")
        SettingRow(
            label = "角色",
            value = if (user?.isAdmin == true) "管理员" else "普通用户"
        )
        // admin-only：role=user 时整个条目不存在（需求1 彻底隐藏）
        NavGraphSpec.settingsEntries(user?.role).forEach { entry ->
            SettingRow(
                label = entry.label,
                value = "增删改查与角色分配",
                accent = true,
                onClick = { onNavigate(entry.route) }
            )
        }
        AwwRule()

        // ---- 服务器 ----
        SectionTitle(number = "02", text = "服务器")
        SettingRow(
            label = "MTmusic 服务",
            value = settings.serverUrl,
            onClick = { serverDraft = settings.serverUrl; editServer = true }
        )
        AwwRule()

        // v1.6.0 需求2：用户要求「设置中的刷新状态改成与服务器的延迟和网速测试」。
        // v1.5.0 曾把网络测试另起一块加在下面，但「刷新状态」这一行还留着，两处功能重叠。
        // 现在合并成一块：网络测试（延迟/带宽）在上，顺手带回的服务器统计在下，
        // 一次点击同时给出"快不快"和"库里有多少"，不再有第二个"刷新"入口。
        SectionTitle(number = "03", text = "网络测试")
        NetworkTestPanel(state = netTest, onRun = { vm.runNetworkTest() })
        val counts = systemInfo?.counts
        SettingRow(label = "曲库歌曲", value = counts?.tracks?.toString() ?: "—")
        SettingRow(label = "进行中任务", value = counts?.queue?.toString() ?: "—")
        SettingRow(label = "已入库", value = counts?.imported?.toString() ?: "—")
        SettingRow(
            label = "刷新状态",
            value = if (systemBusy) "请求中…" else "测延迟/网速并更新统计",
            onClick = {
                vm.runNetworkTest()
                vm.refreshSystem()
            }
        )
        SettingRow(
            label = "触发 Navidrome 扫描",
            value = "歌曲入库后同步媒体库",
            onClick = { vm.triggerScan() }
        )
        AwwRule()

        // ---- 外观 ----
        SectionTitle(number = "04", text = "外观")
        listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (mode, label) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { vm.setThemeMode(mode) }
                    .padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s3),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AwwLabel(text = label, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                // 选中用 ✓ + 强调色；未选只留空位，不放空心圆（避免"一排单选圈"的组件感）
                if (settings.themeMode == mode) {
                    AwwMono(text = "✓ 已选", color = AwwColors.Accent)
                }
            }
        }

        // v1.13.0：界面风格。六套风格共用同一份页面代码 —— 颜色/圆角/间距/字号/动效
        // 全部来自 StyleSpecs，切换时整棵子树 key() 重建，页面无需各自适配。
        Spacer(Modifier.height(AwwSpace.s4))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s2),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AwwMono(text = "STYLE", color = AwwColors.Accent)
            Spacer(Modifier.width(AwwSpace.s3))
            AwwLabel(text = "界面风格", color = AwwColors.Ink3)
            Spacer(Modifier.weight(1f))
            AwwMono(
                text = AppStyle.fromId(settings.appStyle).displayName,
                color = AwwColors.Ink2
            )
        }
        Spacer(Modifier.height(AwwSpace.s2))
        StylePicker(
            current = AppStyle.fromId(settings.appStyle),
            // 预览必须跟随"当前真正生效的明暗"：显式选了深/浅就用它，
            // 跟随系统时用系统值 —— 否则预览出来的配色和切过去看到的不是一回事。
            dark = when (settings.themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            },
            onSelect = { vm.setAppStyle(it) }
        )
        AwwRule()

        // ---- 音效（v1.16.0）----
        SectionTitle(number = "05", text = "音效")
        SettingRow(
            label = "均衡器",
            value = if (eqUi.enabled) eqUi.presetName else "未开启 · " + eqUi.presetName,
            onClick = { onNavigate(Routes.EQ) }
        )
        SettingRow(
            label = "当前输出",
            value = eqUi.output.kind.label +
                (if (eqUi.output.name.isNotBlank()) " · " + eqUi.output.name else "")
        )
        eqUi.recommendation?.let { rec ->
            SettingRow(label = "建议预设", value = rec.name, accent = true, onClick = { onNavigate(Routes.EQ) })
        }
        AwwRule()

        // ---- 车载（v1.16.0）----
        SectionTitle(number = "06", text = "车载")
        SettingRow(
            label = "车载歌词",
            value = carUi.settings.mode.label + (if (carUi.active) " · 推送中" else ""),
            onClick = { onNavigate(Routes.CAR_LYRICS) }
        )
        SettingRow(
            label = "车机识别",
            value = if (carUi.output.isCar) {
                "已识别" + (if (carUi.output.name.isNotBlank()) " · " + carUi.output.name else "")
            } else {
                "未识别（可强制开启）"
            }
        )
        AwwRule()

        // ---- 关于 ----
        SectionTitle(number = "07", text = "关于")
        SettingRow(
            label = "检查更新",
            value = "当前 " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")",
            onClick = { showUpdate = true }
        )
        SettingRow(
            label = "退出登录",
            value = "",
            accent = true,
            onClick = { showLogout = true }
        )

        Spacer(Modifier.height(AwwSpace.s16))
    }
}

/** 小节标题：mono 编号 + uppercase 小标签 + 细线，替代原 M3 labelMedium 灰字。 */
@Composable
private fun SectionTitle(number: String, text: String) {
    Spacer(Modifier.height(AwwSpace.s8))
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AwwMono(text = number, color = AwwColors.Accent)
        Spacer(Modifier.width(AwwSpace.s3))
        AwwLabel(text = text, color = AwwColors.Ink3)
    }
}

/**
 * 设置行：左侧 uppercase 小标签，右侧值（mono）。可点时整行可点击并在最右给箭头。
 * 副标题不再独立一行 —— 规范要求克制层级，两个文字层级已经够用。
 */
@Composable
private fun SettingRow(
    label: String,
    value: String,
    accent: Boolean = false,
    valueColorOverride: Color? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s4),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AwwLabel(text = label, color = AwwColors.Ink3)
        Spacer(Modifier.weight(1f))
        if (value.isNotBlank()) {
            AwwMono(
                text = value,
                color = valueColorOverride
                    ?: if (accent) AwwColors.Accent else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = AwwSpace.s4)
            )
        }
        if (onClick != null) {
            Spacer(Modifier.width(AwwSpace.s2))
            AwwMono(text = "→", color = AwwColors.Ink3)
        }
    }
}

/**
 * v1.5.0 网络测试面板（v1.12.0 重新配色）。
 *
 * 展示原则不变：
 *   - 未测过时只给一句"点下面的按钮"，不预先摆一排 "—"，避免看起来像功能坏了。
 *   - 测完把每一次采样都列出来（中位数 + 每次耗时），用户能看出是不是偶发抖动。
 *   - 带宽用实测读到的字节数算，并注明"只下载前 2MB"，让数字可被怀疑也可被理解。
 */
@Composable
private fun NetworkTestPanel(state: SettingsViewModel.NetworkTest, onRun: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s2)) {
        val rtt = state.rtt
        val speed = state.speed

        if (state.running) {
            AwwMono(text = "测试中…", color = AwwColors.Accent)
        } else if (rtt == null && speed == null && state.error == null) {
            Text(
                "测量与服务器的延迟和下载速度，判断瓶颈在本地网络还是服务端。",
                style = AwwTheme.type.bodySmall,
                color = AwwColors.Ink3
            )
        } else {
            rtt?.let {
                MetricRow("服务器延迟", it.medianMs.toString() + " ms", qualityColor(it.medianMs))
                AwwMono(
                    text = "每次 " + com.musicpipe.player.data.remote.formatSamples(it.samplesMs) +
                        " · 最快 " + it.minMs + "ms",
                    color = AwwColors.Ink3,
                    modifier = Modifier.padding(bottom = AwwSpace.s2)
                )
            }
            speed?.let {
                MetricRow("下载速度", it.humanReadable, qualityColorBySpeed(it.bytesPerSecond))
                AwwMono(
                    text = "读取 " + com.musicpipe.player.data.remote.humanBytes(it.bytesRead) +
                        " · 用时 " + it.elapsedMs + "ms（仅前 2MB）",
                    color = AwwColors.Ink3,
                    modifier = Modifier.padding(bottom = AwwSpace.s2)
                )
            }
            state.error?.let {
                Text(it, style = AwwTheme.type.bodySmall, color = AwwColors.Accent)
            }
        }

        Spacer(Modifier.height(AwwSpace.s3))
        AwwLabel(
            text = if (state.testedAt == null) "开始测试 →" else "重新测试 →",
            color = AwwColors.Accent,
            modifier = Modifier
                .clickable(enabled = !state.running, onClick = onRun)
                .padding(vertical = AwwSpace.s2)
        )
    }
}

@Composable
private fun MetricRow(label: String, value: String, valueColor: Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = AwwSpace.s1),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AwwLabel(text = label, color = AwwColors.Ink3)
        Spacer(Modifier.weight(1f))
        Text(value, style = AwwTheme.type.body, color = valueColor)
    }
}

/**
 * 延迟分档。<80ms 正常（强调色）/ <200ms 一般（琥珀）/ 其余偏慢（红）。
 * 注意：这里刻意保留三档彩色 —— 它是**诊断数据**而不是装饰，
 * 状态色不属于"唯一强调色"的面积预算（§3 讲的是界面装饰）。
 */
private fun qualityColor(ms: Long): Color = when {
    ms < 80 -> AwwColors.Accent
    ms < 200 -> Color(0xFFE6A23C)
    else -> Color(0xFFF56C6C)
}

/** 带宽分档：>=2MB/s 正常 / >=500KB/s 一般 / 其余偏慢。 */
private fun qualityColorBySpeed(bps: Double): Color = when {
    bps >= 2.0 * 1024 * 1024 -> AwwColors.Accent
    bps >= 500 * 1024 -> Color(0xFFE6A23C)
    else -> Color(0xFFF56C6C)
}
