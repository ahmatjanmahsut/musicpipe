package com.musicpipe.player.ui.update

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musicpipe.player.di.LocalAppContainer
import com.musicpipe.player.ui.theme.DsoColors
import com.musicpipe.player.util.VersionUtil

/**
 * 检查更新 dialog (docs/01 §6): check → compare → download with progress →
 * MD5 verify → unknown-sources permission → system installer. Failures are
 * retryable; dismissing never affects the installed version.
 */
@Composable
fun UpdateDialog(onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val context: Context = LocalContext.current
    val vm: UpdateViewModel = viewModel(factory = UpdateViewModel.factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val installedOnce = remember { mutableStateOf(false) }

    // Only auto-check from a settled state; reopening mid-download/mid-prompt
    // must not clobber an in-flight Downloading/Available/NeedPermission state.
    LaunchedEffect(Unit) {
        when (val s = vm.state.value) {
            is UpdateViewModel.UiState.Idle,
            is UpdateViewModel.UiState.Failed,
            is UpdateViewModel.UiState.UpToDate -> vm.check()
            else -> Unit
        }
    }

    // fire the installer exactly once when ready
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
            body = { Text("正在检查新版本…") }
            confirmLabel = null; onConfirm = null
        }
        is UpdateViewModel.UiState.UpToDate -> {
            title = "检查更新"
            body = { Text("当前已是最新版本（" + s.currentVersionName + "）") }
            confirmLabel = null; onConfirm = null
        }
        is UpdateViewModel.UiState.Available -> {
            title = "发现新版本 " + s.release.versionName
            body = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "大小：" + VersionUtil.humanReadableSize(s.release.size),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val changelog = s.release.changelog
                    if (!changelog.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("更新说明", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(changelog, style = MaterialTheme.typography.bodyMedium)
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
                        LinearProgressIndicator(
                            progress = { s.progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = DsoColors.AppRed, trackColor = MaterialTheme.colorScheme.outlineVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            (s.progress * 100).toInt().toString() + "%",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = DsoColors.AppRed, trackColor = MaterialTheme.colorScheme.outlineVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "正在下载…",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            confirmLabel = null; onConfirm = null
        }
        is UpdateViewModel.UiState.NeedPermission -> {
            title = "需要安装权限"
            body = { Text("安装包已下载并通过 MD5 校验。请授予 MusicPipe「安装未知应用」权限后继续。") }
            confirmLabel = "去授权"
            onConfirm = {
                try { context.startActivity(container.updateRepository.unknownSourcesSettingsIntent()) } catch (_: Throwable) { }
            }
        }
        is UpdateViewModel.UiState.ReadyToInstall -> {
            title = "准备安装"
            body = { Text("正在调起系统安装器…") }
            confirmLabel = null; onConfirm = null
        }
        is UpdateViewModel.UiState.Failed -> {
            title = "更新失败"
            body = { Text(s.message + "\n当前版本仍可正常使用。") }
            confirmLabel = if (s.retryable) "重试" else null
            onConfirm = if (s.retryable) ({ vm.retry() }) else null
        }
    }

    // re-check permission when user returns from settings
    val needFile = (state as? UpdateViewModel.UiState.NeedPermission)?.file
    LifecycleResumeEffect(needFile) {
        if (needFile != null) vm.onPermissionResult(needFile)
        onPauseOrDispose { }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { body() },
        confirmButton = {
            if (onConfirm != null && confirmLabel != null) {
                TextButton(onClick = onConfirm) { Text(confirmLabel, color = DsoColors.AppRed) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    if (state is UpdateViewModel.UiState.UpToDate) "关闭" else "取消",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}