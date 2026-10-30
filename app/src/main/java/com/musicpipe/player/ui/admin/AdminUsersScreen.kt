package com.musicpipe.player.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musicpipe.player.data.remote.dto.UserRowDto
import com.musicpipe.player.di.LocalAppContainer
import com.musicpipe.player.ui.components.ErrorBanner
import com.musicpipe.player.ui.components.LoadingBlock
import com.musicpipe.player.ui.design.AwwLabel
import com.musicpipe.player.ui.design.AwwMono
import com.musicpipe.player.ui.design.AwwPageHeader
import com.musicpipe.player.ui.design.AwwRule
import com.musicpipe.player.ui.theme.AwwColors
import com.musicpipe.player.ui.theme.AwwShapes
import com.musicpipe.player.ui.theme.AwwSpace
import com.musicpipe.player.ui.theme.AwwTheme
import com.musicpipe.player.util.TimeFormat

/**
 * 用户管理（仅 admin 可见——路由只在 role==admin 时注册进 NavHost）。
 *
 * v1.12.0：按 DESIGN.md 重写。角色标签从「彩色药丸背景」改成「mono 大写标签 + 强调色」；
 * 编辑对话框的输入框换成下划线式（与登录页同一套语言），去掉 M3 OutlinedTextField 的厚边框；
 * 分页从两个 TextButton 改成 mono 计数 + 左右箭头标签。
 */
@Composable
fun AdminUsersScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: UserAdminViewModel = viewModel(factory = UserAdminViewModel.factory(container))
    val state by vm.state.collectAsStateWithLifecycle()

    var showCreate by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<UserRowDto?>(null) }
    var deleteTarget by remember { mutableStateOf<UserRowDto?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    toast?.let { msg ->
        AlertDialog(
            onDismissRequest = { toast = null },
            containerColor = MaterialTheme.colorScheme.background,
            shape = AwwShapes.Square,
            title = { Text("提示", style = AwwTheme.type.h3) },
            text = { Text(msg, style = AwwTheme.type.bodySmall, color = AwwColors.Ink2) },
            confirmButton = {
                AwwLabel(
                    text = "好",
                    color = AwwColors.Accent,
                    modifier = Modifier.clickable { toast = null }.padding(AwwSpace.s3)
                )
            }
        )
    }

    if (showCreate) {
        UserEditDialog(
            title = "新增用户",
            initialUsername = "",
            initialRole = "user",
            usernameEditable = true,
            onCancel = { showCreate = false },
            onSubmit = { username, password, role ->
                vm.create(username, password, role) { err ->
                    showCreate = false
                    if (err != null) toast = err
                }
            }
        )
    }

    editTarget?.let { u ->
        UserEditDialog(
            title = "编辑用户",
            initialUsername = u.username,
            initialRole = u.role,
            usernameEditable = false,
            onCancel = { editTarget = null },
            onSubmit = { _, password, role ->
                vm.update(u.id, password, role) { err ->
                    editTarget = null
                    if (err != null) toast = err
                }
            }
        )
    }

    deleteTarget?.let { u ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = MaterialTheme.colorScheme.background,
            shape = AwwShapes.Square,
            title = { Text("删除用户", style = AwwTheme.type.h3) },
            text = {
                Text(
                    "确定删除「" + u.username + "」吗？此操作不可恢复。",
                    style = AwwTheme.type.bodySmall,
                    color = AwwColors.Ink2
                )
            },
            confirmButton = {
                AwwLabel(
                    text = "删除",
                    color = AwwColors.Accent,
                    modifier = Modifier.clickable {
                        vm.delete(u.id) { err ->
                            deleteTarget = null
                            if (err != null) toast = err
                        }
                    }.padding(AwwSpace.s3)
                )
            },
            dismissButton = {
                AwwLabel(
                    text = "取消",
                    color = AwwColors.Ink3,
                    modifier = Modifier.clickable { deleteTarget = null }.padding(AwwSpace.s3)
                )
            }
        )
    }

    Column(Modifier.fillMaxSize()) {
        AwwPageHeader(
            index = "10",
            label = "ADMIN · USERS",
            title = "用户管理",
            meta = state.total.toString().padStart(3, '0') + " 个账号 · 第 " + state.page + " / " + state.pages + " 页",
            onBack = onBack,
            trailing = {
                AwwLabel(
                    text = "+ 新增",
                    color = AwwColors.Accent,
                    modifier = Modifier.clickable { showCreate = true }
                )
            }
        )

        if (state.loading && state.items.isEmpty()) {
            LoadingBlock()
        } else {
            state.error?.let { ErrorBanner("加载失败：" + it) }
            LazyColumn(Modifier.weight(1f)) {
                items(state.items, key = { it.id }, contentType = { "user" }) { u ->
                    UserRow(user = u, onEdit = { editTarget = u }, onDelete = { deleteTarget = u })
                    AwwRule()
                }
                item(key = "pager", contentType = "footer") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s4),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val canPrev = state.page > 1
                        AwwLabel(
                            text = "← 上一页",
                            color = if (canPrev) MaterialTheme.colorScheme.onSurface else AwwColors.Ink3,
                            modifier = Modifier
                                .clickable(enabled = canPrev) { vm.load(state.page - 1) }
                                .padding(AwwSpace.s2)
                        )
                        AwwMono(
                            text = state.page.toString().padStart(2, '0') + " / " +
                                state.pages.toString().padStart(2, '0'),
                            color = AwwColors.Ink3
                        )
                        val canNext = state.page < state.pages
                        AwwLabel(
                            text = "下一页 →",
                            color = if (canNext) MaterialTheme.colorScheme.onSurface else AwwColors.Ink3,
                            modifier = Modifier
                                .clickable(enabled = canNext) { vm.load(state.page + 1) }
                                .padding(AwwSpace.s2)
                        )
                    }
                }
                item(key = "bottom_pad", contentType = "spacer") { Spacer(Modifier.height(AwwSpace.s8)) }
            }
        }
    }
}

@Composable
private fun UserRow(user: UserRowDto, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    user.username,
                    style = AwwTheme.type.body,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(AwwSpace.s2))
                if (user.role == "admin") {
                    AwwLabel(text = "ADMIN", color = AwwColors.Accent)
                } else {
                    AwwLabel(text = "USER", color = AwwColors.Ink3)
                }
            }
            AwwMono(
                text = "创建 " + TimeFormat.formatDateTime(user.createdAt),
                color = AwwColors.Ink3
            )
        }
        AwwLabel(
            text = "编辑",
            color = AwwColors.Ink2,
            modifier = Modifier.clickable { onEdit() }.padding(AwwSpace.s2)
        )
        Spacer(Modifier.width(AwwSpace.s2))
        AwwLabel(
            text = "删除",
            color = AwwColors.Accent,
            modifier = Modifier.clickable { onDelete() }.padding(AwwSpace.s2)
        )
    }
}

/** 下划线式输入框：与登录页同一套语言，替代 M3 OutlinedTextField 的厚边框。 */
@Composable
private fun UnderlineField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
    password: Boolean = false,
    placeholder: String = ""
) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = AwwSpace.s2)) {
        AwwLabel(text = label, color = if (focused) AwwColors.Accent else AwwColors.Ink3)
        Spacer(Modifier.height(AwwSpace.s1))
        Box {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(
                    placeholder,
                    style = AwwTheme.type.bodySmall.copy(
                        color = AwwColors.Ink3
                    )
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = AwwTheme.type.bodySmall.copy(
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else AwwColors.Ink3
                ),
                cursorBrush = SolidColor(AwwColors.Accent),
                visualTransformation = if (password) PasswordVisualTransformation()
                else VisualTransformation.None,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused }
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

@Composable
private fun UserEditDialog(
    title: String,
    initialUsername: String,
    initialRole: String,
    usernameEditable: Boolean,
    onCancel: () -> Unit,
    onSubmit: (username: String, password: String, role: String) -> Unit
) {
    var username by remember { mutableStateOf(initialUsername) }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(initialRole) }
    var roleExpanded by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = MaterialTheme.colorScheme.background,
        shape = AwwShapes.Square,
        title = { Text(title, style = AwwTheme.type.h3) },
        text = {
            Column {
                UnderlineField(
                    label = "用户名",
                    value = username,
                    onValueChange = { username = it },
                    enabled = usernameEditable,
                    placeholder = if (usernameEditable) "至少 3 个字符" else ""
                )
                UnderlineField(
                    label = if (usernameEditable) "密码" else "新密码（留空则不修改）",
                    value = password,
                    onValueChange = { password = it },
                    password = true,
                    placeholder = if (usernameEditable) "至少 6 位" else ""
                )
                // 角色：两个互斥选项，比下拉框更直观（只有两种角色）。
                AwwLabel(
                    text = "角色",
                    color = AwwColors.Ink3,
                    modifier = Modifier.padding(top = AwwSpace.s3)
                )
                Row(Modifier.fillMaxWidth().padding(top = AwwSpace.s2)) {
                    RoleOption(
                        label = "普通用户",
                        code = "user",
                        selected = role == "user",
                        onClick = { role = "user" },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(AwwSpace.s2))
                    RoleOption(
                        label = "管理员",
                        code = "admin",
                        selected = role == "admin",
                        onClick = { role = "admin" },
                        modifier = Modifier.weight(1f)
                    )
                }
                localError?.let {
                    Spacer(Modifier.height(AwwSpace.s2))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(2.dp).height(14.dp).background(AwwColors.Accent))
                        Spacer(Modifier.width(AwwSpace.s2))
                        Text(it, color = AwwColors.Accent, style = AwwTheme.type.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            AwwLabel(
                text = "保存",
                color = AwwColors.Accent,
                modifier = Modifier.clickable {
                    when {
                        usernameEditable && username.isBlank() -> localError = "用户名不能为空"
                        usernameEditable && password.length < 6 -> localError = "密码至少 6 位"
                        else -> onSubmit(username, password, role)
                    }
                }.padding(AwwSpace.s3)
            )
        },
        dismissButton = {
            AwwLabel(
                text = "取消",
                color = AwwColors.Ink3,
                modifier = Modifier.clickable { onCancel() }.padding(AwwSpace.s3)
            )
        }
    )
}

@Composable
private fun RoleOption(
    label: String,
    code: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .border(1.dp, if (selected) AwwColors.Accent else AwwColors.Line, AwwShapes.Square)
            .clickable { onClick() }
            .padding(AwwSpace.s3)
    ) {
        AwwMono(
            text = code.uppercase(),
            color = if (selected) AwwColors.Accent else AwwColors.Ink3
        )
        Spacer(Modifier.height(AwwSpace.s1))
        Text(
            label,
            style = AwwTheme.type.bodySmall,
            color = if (selected) MaterialTheme.colorScheme.onSurface else AwwColors.Ink2
        )
    }
}
