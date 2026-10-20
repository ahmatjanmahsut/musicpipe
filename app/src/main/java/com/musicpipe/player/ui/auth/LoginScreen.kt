package com.musicpipe.player.ui.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musicpipe.player.ui.components.AppOutlinedTextField
import com.musicpipe.player.data.model.AppUser
import com.musicpipe.player.di.LocalAppContainer
import com.musicpipe.player.ui.theme.DsoColors

/**
 * Login + register (requirement 1). DsoMusic style: white page, red theme button,
 * rounded input fields on #F5F5F5 (docs/03 §登录页).
 */
@Composable
fun LoginScreen(onLoggedIn: (AppUser) -> Unit) {
    val container = LocalAppContainer.current
    val vm: AuthViewModel = viewModel(factory = AuthViewModel.factory(container))
    val state by vm.state.collectAsStateWithLifecycle()

    var mode by remember { mutableStateOf("login") } // login | register
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var password2 by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    var showServerDialog by remember { mutableStateOf(false) }
    val serverUrl by vm.serverUrl.collectAsStateWithLifecycle()

    // react to logged-in
    (state as? AuthViewModel.UiState.LoggedIn)?.let { onLoggedIn(it.user) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(96.dp))
        Text(
            "MusicPipe",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = DsoColors.AppRed
        )
        Spacer(Modifier.height(6.dp))
        Text(if (mode == "login") "登录到你的音乐世界" else "创建新账号", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(
            "与 MusicPipe 控制台共用同一账号体系",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        // 登录前可配置服务器地址（外网访问需要，v1.3.0）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showServerDialog = true }
                .padding(vertical = 4.dp)
        ) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(6.dp))
            Text(
                "服务器：" + serverUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(26.dp))

    if (showServerDialog) {
        var urlDraft by remember { mutableStateOf(serverUrl) }
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            title = { Text("服务器地址") },
            text = {
                Column {
                    AppOutlinedTextField(
                        value = urlDraft,
                        onValueChange = { urlDraft = it },
                        label = { Text("地址") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "支持 http:// 或 https://。\n外网访问填公网域名（如 https://music.example.com），\n局域网可填路由器 IP（如 http://192.168.1.10:7800）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "恢复默认",
                        style = MaterialTheme.typography.bodySmall,
                        color = DsoColors.HighlightBlue,
                        modifier = Modifier.clickable {
                            urlDraft = com.musicpipe.player.data.prefs.PrefsRepository.DEFAULT_SERVER_URL
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (urlDraft.isNotBlank()) vm.setServerUrl(urlDraft)
                    showServerDialog = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showServerDialog = false }) { Text("取消") } }
        )
    }

        AppOutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("用户名") },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(14.dp))
        AppOutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        )
        if (mode == "register") {
            Spacer(Modifier.height(14.dp))
            AppOutlinedTextField(
                value = password2,
                onValueChange = { password2 = it },
                label = { Text("确认密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        val errorMessage = localError ?: (state as? AuthViewModel.UiState.Error)?.message
        if (errorMessage != null) {
            Spacer(Modifier.height(12.dp))
            Text(errorMessage, color = DsoColors.AppRed, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = {
                localError = null
                (state as? AuthViewModel.UiState.Error)?.let { vm.consumeError() }
                when {
                    username.isBlank() || password.isBlank() -> localError = "请输入用户名和密码"
                    mode == "register" && password != password2 -> localError = "两次输入的密码不一致"
                    mode == "register" && password.length < 6 -> localError = "密码至少 6 位"
                    mode == "login" -> vm.login(username, password)
                    else -> vm.register(username, password)
                }
            },
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DsoColors.AppRed),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            if (state is AuthViewModel.UiState.Busy) {
                CircularProgressIndicator(color = DsoColors.PageWhite, strokeWidth = 2.dp, modifier = Modifier.height(20.dp))
            } else {
                Text(if (mode == "login") "登 录" else "注 册", fontSize = 16.sp)
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = {
                mode = if (mode == "login") "register" else "login"
                localError = null
                (state as? AuthViewModel.UiState.Error)?.let { vm.consumeError() }
            }) {
                Text(
                    if (mode == "login") "没有账号？注册" else "已有账号？登录",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}
