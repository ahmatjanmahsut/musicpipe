package com.musicpipe.player.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musicpipe.player.BuildConfig
import com.musicpipe.player.data.remote.dto.LatestReleaseDto
import com.musicpipe.player.data.repo.UpdateRepository
import com.musicpipe.player.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/** Requirement 5 / docs/01 §6 update flow state machine. */
class UpdateViewModel(private val container: AppContainer) : ViewModel() {

    sealed class UiState {
        data object Idle : UiState()
        data object Checking : UiState()
        data class UpToDate(val currentVersionName: String) : UiState()
        data class Available(val release: LatestReleaseDto) : UiState()
        data class Downloading(val progress: Float) : UiState()
        /** Verified APK ready but 未知来源安装 permission missing → guide user. */
        data class NeedPermission(val file: File) : UiState()
        data class ReadyToInstall(val file: File) : UiState()
        data class Failed(val message: String, val retryable: Boolean) : UiState()
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state

    private var pending: LatestReleaseDto? = null

    fun check() {
        _state.value = UiState.Checking
        viewModelScope.launch {
            when (val r = container.updateRepository.checkForUpdate(
                BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME
            )) {
                is UpdateRepository.CheckResult.UpToDate -> _state.value = UiState.UpToDate(r.currentVersionName)
                is UpdateRepository.CheckResult.UpdateAvailable -> {
                    pending = r.release
                    _state.value = UiState.Available(r.release)
                }
                UpdateRepository.CheckResult.NoRelease -> _state.value = UiState.UpToDate(BuildConfig.VERSION_NAME)
                is UpdateRepository.CheckResult.Failed -> _state.value = UiState.Failed(r.message, r.retryable)
            }
        }
    }

    fun startDownload() {
        val release = pending ?: return
        _state.value = UiState.Downloading(0f)
        viewModelScope.launch {
            val result = container.updateRepository.download(release) { done, total ->
                _state.value = UiState.Downloading(
                    if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else -1f
                )
            }
            _state.value = when (result) {
                is UpdateRepository.DownloadResult.Success ->
                    if (container.updateRepository.canRequestPackageInstalls()) UiState.ReadyToInstall(result.file)
                    else UiState.NeedPermission(result.file)
                is UpdateRepository.DownloadResult.ChecksumFailed ->
                    UiState.Failed("MD5 校验失败，安装包已删除（当前版本不受影响），请重试下载", true)
                is UpdateRepository.DownloadResult.Failed ->
                    UiState.Failed(result.message, true)
            }
        }
    }

    /** Called after returning from the unknown-sources settings page. */
    fun onPermissionResult(file: File) {
        _state.value = if (container.updateRepository.canRequestPackageInstalls()) {
            UiState.ReadyToInstall(file)
        } else {
            UiState.Failed("未授予「安装未知应用」权限，无法安装更新", true)
        }
    }

    fun retry() {
        when (_state.value) {
            is UiState.Failed -> if (pending != null) startDownload() else check()
            else -> check()
        }
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = UpdateViewModel(container) as T
        }
    }
}
