package com.musicpipe.player.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musicpipe.player.data.model.AppUser
import com.musicpipe.player.data.prefs.PrefsRepository
import com.musicpipe.player.data.remote.ApiException
import com.musicpipe.player.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AuthViewModel(private val container: AppContainer) : ViewModel() {

    sealed class UiState {
        data object Idle : UiState()
        data object Busy : UiState()
        data class Error(val message: String) : UiState()
        data class LoggedIn(val user: AppUser) : UiState()
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state

    /** 当前服务器地址（登录前可配置，v1.3.0 外网访问支持）。 */
    val serverUrl: StateFlow<String> = container.prefs.serverUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, PrefsRepository.DEFAULT_SERVER_URL)

    fun setServerUrl(url: String) {
        val v = url.trim()
        if (v.isEmpty()) return
        viewModelScope.launch { container.prefs.setServerUrl(v) }
    }

    fun login(username: String, password: String) {
        if (_state.value is UiState.Busy) return
        _state.value = UiState.Busy
        viewModelScope.launch {
            try {
                val user = container.authRepository.login(username.trim(), password)
                _state.value = UiState.LoggedIn(user)
            } catch (e: ApiException) {
                _state.value = UiState.Error(humanize(e))
            } catch (t: Throwable) {
                _state.value = UiState.Error(t.message ?: "网络错误，请检查服务器地址")
            }
        }
    }

    fun register(username: String, password: String) {
        if (_state.value is UiState.Busy) return
        _state.value = UiState.Busy
        viewModelScope.launch {
            try {
                container.authRepository.register(username.trim(), password)
                // auto-login after successful registration
                val user = container.authRepository.login(username.trim(), password)
                _state.value = UiState.LoggedIn(user)
            } catch (e: ApiException) {
                _state.value = UiState.Error(humanize(e))
            } catch (t: Throwable) {
                _state.value = UiState.Error(t.message ?: "网络错误，请检查服务器地址")
            }
        }
    }

    fun consumeError() { _state.value = UiState.Idle }

    private fun humanize(e: ApiException): String = when (e.code) {
        "user_not_found" -> "用户不存在"
        "wrong_password" -> "密码错误"
        "username_exists" -> "用户名已被占用"
        "invalid_username" -> "用户名不合法（3-32 位字母、数字、下划线或中文）"
        "invalid_password" -> "密码至少 6 位"
        "unauthorized" -> "登录状态已失效，请重新登录"
        "forbidden" -> "需要管理员权限"
        else -> e.message
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AuthViewModel(container) as T
        }
    }
}
