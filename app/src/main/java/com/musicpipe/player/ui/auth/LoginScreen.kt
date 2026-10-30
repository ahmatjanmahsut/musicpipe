package com.musicpipe.player.ui.auth

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musicpipe.player.data.model.AppUser
import com.musicpipe.player.di.LocalAppContainer
import com.musicpipe.player.ui.design.AwwButton
import com.musicpipe.player.ui.design.AwwLabel
import com.musicpipe.player.ui.design.AwwMono
import com.musicpipe.player.ui.design.AwwRule
import com.musicpipe.player.ui.design.MaskReveal
import com.musicpipe.player.ui.theme.AwwColors
import com.musicpipe.player.ui.theme.AwwShapes
import com.musicpipe.player.ui.theme.AwwSpace
import com.musicpipe.player.ui.theme.AwwTheme

/**
 * 登录 / 注册（需求1）。
 *
 * v1.12.0 按 DESIGN.md 重构：
 * - 首页级排版：mono 编号 + hero 大字 "MTmusic" + 一句克制说明，文字是主角，
 *   而不是"居中图标 + 卡片包住表单"。
 * - 输入框改为下划线式（1dp 底边 + 大写小标签），焦点态把底边换成强调色 ——
 *   这是 §6 焦点态在移动端的等价表达，也避免了规范明令禁止的"大圆角灰底药丸"。
 * - 主操作走 [AwwButton] filled（透明底+1px 线的反面态），加载态用同尺寸的
 *   mono 文案替换，不再塞 CircularProgressIndicator（避免按钮高度跳动）。
 *
 * 业务逻辑与 v1.10.0 完全一致（含服务器地址配置对话框、本地校验顺序、
 * "回调移进 LaunchedEffect" 的 C4 修复）。
 */
@Composable
fun LoginScreen(onLoggedIn: (AppUser) -> Unit) {
    val container = LocalAppContainer.current
    val vm: AuthViewModel = viewModel(factory = AuthViewModel.factory(container))
    val state by vm.state.collectAsStateWithLifecycle()

    var mode by remember { mutableStateOf("login") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var password2 by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    var showServerDialog by remember { mutableStateOf(false) }
    val serverUrl by vm.serverUrl.collectAsStateWithLifecycle()

    // v1.10.0（C4）：回调移进 LaunchedEffect。
    // 在组合体里直接调用外部回调，一旦它写任何 state 就会触发
    // "Writing to state during composition" 甚至反复重启组合；副作用必须放进 effect。
    val loggedIn = (state as? AuthViewModel.UiState.LoggedIn)?.user
    LaunchedEffect(loggedIn) {
        loggedIn?.let(onLoggedIn)
    }

    if (showServerDialog) {
        var urlDraft by remember { mutableStateOf(serverUrl) }
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            containerColor = MaterialTheme.colorScheme.background,
            shape = AwwShapes.Square,
            title = { Text("服务器地址", style = AwwTheme.type.h3) },
            text = {
                Column {
                    UnderlineField(
                        label = "地址",
                        value = urlDraft,
                        onValueChange = { urlDraft = it },
                        password = false
                    )
                    Spacer(Modifier.height(AwwSpace.s4))
                    Text(
                        "支持 http:// 或 https://。\n外网访问填公网域名（如 https://music.example.com），\n局域网可填路由器 IP（如 http://192.168.1.10:7800）。",
                        style = AwwTheme.type.bodySmall,
                        color = AwwColors.Ink3
                    )
                    Spacer(Modifier.height(AwwSpace.s3))
                    AwwLabel(
                        text = "恢复默认",
                        color = AwwColors.Accent,
                        modifier = Modifier.clickable {
                            urlDraft = com.musicpipe.player.data.prefs.PrefsRepository.DEFAULT_SERVER_URL
                        }
                    )
                }
            },
            confirmButton = {
                AwwLabel(
                    text = "保存",
                    color = AwwColors.Accent,
                    modifier = Modifier
                        .clickable {
                            if (urlDraft.isNotBlank()) vm.setServerUrl(urlDraft)
                            showServerDialog = false
                        }
                        .padding(AwwSpace.s3)
                )
            },
            dismissButton = {
                AwwLabel(
                    text = "取消",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable { showServerDialog = false }
                        .padding(AwwSpace.s3)
                )
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AwwSpace.s6)
    ) {
        Spacer(Modifier.height(AwwSpace.s24))

        Row(verticalAlignment = Alignment.CenterVertically) {
            AwwLabel(text = "MTMUSIC", color = AwwColors.Accent)
            Spacer(Modifier.weight(1f))
            AwwMono(text = "00 / AUTH", color = AwwColors.Ink3)
        }
        Spacer(Modifier.height(AwwSpace.s6))
        AwwRule()

        Spacer(Modifier.height(AwwSpace.s6))
        MaskReveal {
            Text(
                text = "Music\nPipe",
                style = AwwTheme.type.hero,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(AwwSpace.s4))
        Text(
            text = if (mode == "login") "登录到你的音乐世界" else "创建一个新账号",
            style = AwwTheme.type.h3,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(AwwSpace.s2))
        Text(
            text = "与 MTmusic 控制台共用同一账号体系。媒体库在你的服务器上，这个 App 只是入口。",
            style = AwwTheme.type.bodySmall,
            color = AwwColors.Ink3
        )

        Spacer(Modifier.height(AwwSpace.s8))

        // 服务器地址（登录前可改，v1.3.0 需求）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showServerDialog = true }
                .padding(vertical = AwwSpace.s2),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AwwLabel(text = "服务器", color = AwwColors.Ink3)
            Spacer(Modifier.width(AwwSpace.s3))
            AwwMono(
                text = serverUrl,
                color = AwwColors.Ink2,
                modifier = Modifier.weight(1f)
            )
            AwwMono(text = "编辑 →", color = AwwColors.Accent)
        }
        AwwRule()

        Spacer(Modifier.height(AwwSpace.s8))

        UnderlineField(
            label = "用户名",
            value = username,
            onValueChange = { username = it },
            password = false
        )
        Spacer(Modifier.height(AwwSpace.s6))
        UnderlineField(
            label = "密码",
            value = password,
            onValueChange = { password = it },
            password = true
        )
        if (mode == "register") {
            Spacer(Modifier.height(AwwSpace.s6))
            UnderlineField(
                label = "确认密码",
                value = password2,
                onValueChange = { password2 = it },
                password = true
            )
        }

        val errorMessage = localError ?: (state as? AuthViewModel.UiState.Error)?.message
        if (errorMessage != null) {
            Spacer(Modifier.height(AwwSpace.s4))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(2.dp).height(16.dp).background(AwwColors.Accent))
                Spacer(Modifier.width(AwwSpace.s2))
                Text(
                    text = errorMessage,
                    style = AwwTheme.type.bodySmall,
                    color = AwwColors.Accent
                )
            }
        }

        Spacer(Modifier.height(AwwSpace.s8))

        if (state is AuthViewModel.UiState.Busy) {
            // 加载态用同高文案替换，避免按钮高度跳动（原实现内嵌 spinner 会撑高/压扁）。
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(AwwColors.Accent, AwwShapes.Square),
                contentAlignment = Alignment.Center
            ) {
                AwwMono(text = "登录中…", color = AwwColors.OnAccent, style = AwwTheme.type.label)
            }
        } else {
            AwwButton(
                text = if (mode == "login") "登录" else "注册",
                filled = true,
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
                }
            )
        }

        Spacer(Modifier.height(AwwSpace.s4))

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            AwwLabel(
                text = if (mode == "login") "没有账号？注册 →" else "已有账号？登录 →",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable {
                        mode = if (mode == "login") "register" else "login"
                        localError = null
                        (state as? AuthViewModel.UiState.Error)?.let { vm.consumeError() }
                    }
                    .padding(vertical = AwwSpace.s2)
            )
        }

        Spacer(Modifier.height(AwwSpace.s16))
    }
}

/**
 * 下划线输入框（v1.12.0）：1dp 底边 + 大写小标签 + 无容器。
 *
 * 为什么不用 M3 OutlinedTextField：它自带 4dp 圆角描边框，与"细线 + 无卡片"的
 * 整站语言冲突；且它的浮动 label 会在获得焦点时上移+缩小，视觉上跳。
 * 这里 label 常驻上方（固定 11sp），输入区只有一条底线，焦点时底线变强调色。
 */
@Composable
private fun UnderlineField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    password: Boolean
) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        AwwLabel(
            text = label,
            color = if (focused) AwwColors.Accent else AwwColors.Ink3
        )
        Spacer(Modifier.height(AwwSpace.s2))
        Box(Modifier.fillMaxWidth()) {
            if (value.isEmpty()) {
                Text(
                    text = if (password) "••••••" else "—",
                    style = AwwTheme.type.body,
                    color = AwwColors.Ink3
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = if (password) {
                    KeyboardOptions(keyboardType = KeyboardType.Password)
                } else {
                    KeyboardOptions.Default
                },
                textStyle = AwwTheme.type.body.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(AwwColors.Accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AwwSpace.s2)
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(if (focused) AwwColors.Accent else AwwColors.Line)
        )
    }
}
