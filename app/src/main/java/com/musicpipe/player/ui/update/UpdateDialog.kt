package com.musicpipe.player.ui.update

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musicpipe.player.di.LocalAppContainer
import com.musicpipe.player.ui.design.AwwLabel
import com.musicpipe.player.ui.design.AwwMono
import com.musicpipe.player.ui.design.AwwRule
import com.musicpipe.player.ui.design.HairlineProgress
import com.musicpipe.player.ui.theme.AwwColors
import com.musicpipe.player.ui.theme.AwwShapes
import com.musicpipe.player.ui.theme.AwwSpace
import com.musicpipe.player.ui.theme.AwwTheme
import com.musicpipe.player.util.VersionUtil

/**
 * 检查更新（docs/01 §6）：检查 → 比对版本 → 带进度下载 → MD5 校验 → 未知来源权限 → 系统安装器。
 * 失败可重试；关闭弹窗不影响已安装版本（需求5）。
 *
 * v1.12.0 只改视觉：进度条换成 1dp 细线 HairlineProgress，按钮/标题改 mono 与 h3，
 * AlertDialog 用方角 + 背景色容器。状态机与副作用（installer 只 fire 一次、
 * 从系统设置返回后重查权限）与 v1.10.0 完全一致。
 */
@Composable
fun UpdateDialog(onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val context: Context = LocalContext.current
    val vm: UpdateViewModel = viewModel(factory = UpdateViewModel.factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val installedOnce = remember { mutableStateOf(false) }

    // 只在稳定状态下自动检查；重开弹窗时若正在下载/待安装，不能用 check() 覆盖掉。
    LaunchedEffect(Unit) {
        when (vm.state.value) {
            is UpdateViewModel.UiState.Idle,
            is UpdateViewModel.UiState.Failed,
            is UpdateViewModel.UiState.UpToDate -> vm.check()
            else -> Unit
        }
    }

    // 安装器只 fire 一次（否则状态每变一次就再 startActivity 一次）
    LaunchedEffect(state) {
        val s = state
        if (s is UpdateViewModel.UiState.ReadyToInstall && !installedOnce.value) {
            installedOnce.value = true
            try {
                context.startActivity(container.updateRepository.installIntent(s.file))
            } catch (t: Throwable) {
                installedOnce.value = false
            }
        }
    }

    val title: String
    val body: @Composable () -> Unit
    val confirmLabel: String?
    val onConfirm: (() -> Unit)?

    when (val s = state) {
        UpdateViewModel.UiState.Idle, UpdateViewModel.UiState.Checking -> {
            title = "检查更新"
            body = { AwwMono(text = "正在检查新版本…", color = AwwColors.Ink3) }
            confirmLabel = null; onConfirm = null
        }
        is UpdateViewModel.UiState.UpToDate -> {
            title = "检查更新"
            body = {
                Column {
                    Text("当前已是最新版本", style = AwwTheme.type.body)
                    Spacer(Modifier.height(AwwSpace.s2))
                    AwwMono(text = "v" + s.currentVersionName, color = AwwColors.Ink3)
                }
            }
            confirmLabel = null; onConfirm = null
        }
        is UpdateViewModel.UiState.Available -> {
            title = "发现新版本 " + s.release.versionName
            body = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    AwwMono(
                        text = "大小 " + VersionUtil.humanReadableSize(s.release.size),
                        color = AwwColors.Ink3
                    )
                    val changelog = s.release.changelog
                    if (!changelog.isNullOrBlank()) {
                        Spacer(Modifier.height(AwwSpace.s3))
                        AwwRule()
                        Spacer(Modifier.height(AwwSpace.s3))
                        AwwLabel(text = "更新说明", color = AwwColors.Ink3)
                        Spacer(Modifier.height(AwwSpace.s2))
                        Text(changelog, style = AwwTheme.type.bodySmall)
                    }
                }
            }
            confirmLabel = "立即更新"; onConfirm = { vm.startDownload() }
        }
        is UpdateViewModel.UiState.Downloading -> {
            title = "正在下载更新"
            body = {
                Column {
                    if (s.progress >= 0f) {
                        HairlineProgress(progress = s.progress, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(AwwSpace.s2))
                        AwwMono(
                            text = (s.progress * 100).toInt().toString() + "%",
                            color = AwwColors.Accent
                        )
                    } else {
                        AwwMono(text = "正在下载…", color = AwwColors.Accent)
                    }
                }
            }
            confirmLabel = null; onConfirm = null
        }
        is UpdateViewModel.UiState.NeedPermission -> {
            title = "需要安装权限"
            body = {
                Text(
                    "安装包已下载并通过 MD5 校验。请授予 MTmusic「安装未知应用」权限后继续。",
                    style = AwwTheme.type.bodySmall
                )
            }
            confirmLabel = "去授权"
            onConfirm = {
                try { context.startActivity(container.updateRepository.unknownSourcesSettingsIntent()) } catch (_: Throwable) { }
            }
        }
        is UpdateViewModel.UiState.ReadyToInstall -> {
            title = "准备安装"
            body = { AwwMono(text = "正在调起系统安装器…", color = AwwColors.Ink3) }
            confirmLabel = null; onConfirm = null
        }
        is UpdateViewModel.UiState.Failed -> {
            title = "更新失败"
            body = {
                Column {
                    Text(s.message, style = AwwTheme.type.bodySmall, color = AwwColors.Accent)
                    Spacer(Modifier.height(AwwSpace.s2))
                    AwwMono(text = "当前版本仍可正常使用", color = AwwColors.Ink3)
                }
            }
            confirmLabel = if (s.retryable) "重试" else null
            onConfirm = if (s.retryable) ({ vm.retry() }) else null
        }
    }

    // 从系统设置返回后重查权限（否则用户授完权回到 App 仍停在「需要权限」）
    val needFile = (state as? UpdateViewModel.UiState.NeedPermission)?.file
    LifecycleResumeEffect(needFile) {
        if (needFile != null) vm.onPermissionResult(needFile)
        onPauseOrDispose { }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        shape = AwwShapes.Square,
        title = { Text(title, style = AwwTheme.type.h3) },
        text = { body() },
        confirmButton = {
            if (onConfirm != null && confirmLabel != null) {
                AwwLabel(
                    text = confirmLabel,
                    color = AwwColors.Accent,
                    modifier = Modifier.clickable { onConfirm() }.padding(AwwSpace.s3)
                )
            }
        },
        dismissButton = {
            AwwLabel(
                text = if (state is UpdateViewModel.UiState.UpToDate) "关闭" else "取消",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { onDismiss() }.padding(AwwSpace.s3)
            )
        }
    )
}
