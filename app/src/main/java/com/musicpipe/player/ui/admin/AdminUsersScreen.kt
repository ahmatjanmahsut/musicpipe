package com.musicpipe.player.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musicpipe.player.ui.components.AppOutlinedTextField
import com.musicpipe.player.data.remote.dto.UserRowDto
import com.musicpipe.player.di.LocalAppContainer
import com.musicpipe.player.ui.components.ErrorBanner
import com.musicpipe.player.ui.components.LoadingBlock
import com.musicpipe.player.ui.theme.DsoColors
import com.musicpipe.player.util.TimeFormat

/**
 * 用户管理 (admin only — route registered only when role==admin).
 * CRUD + role assignment per docs/01 §3.3.
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
        AlertDialog(onDismissRequest = { toast = null }, confirmButton = {
            TextButton(onClick = { toast = null }) { Text("好") }
        }, text = { Text(msg) })
    }

    if (showCreate) {
        UserEditDialog(
            title = "新增用户", initialUsername = "", initialRole = "user", usernameEditable = true,
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
            title = "编辑用户：" + u.username, initialUsername = u.username, initialRole = u.role,
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
            title = { Text("删除用户") },
            text = { Text("确定删除「" + u.username + "」吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(u.id) { err ->
                        deleteTarget = null
                        if (err != null) toast = err
                    }
                }) { Text("删除", color = DsoColors.AppRed) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } }
        )
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                "用户管理", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { showCreate = true }) { Text("新增", color = DsoColors.AppRed) }
        }
        if (state.loading && state.items.isEmpty()) {
            LoadingBlock()
        } else {
            state.error?.let { ErrorBanner("加载失败：" + it) }
            LazyColumn(Modifier.weight(1f)) {
                items(state.items, key = { it.id }) { u ->
                    UserRow(
                        user = u,
                        onEdit = { editTarget = u },
                        onDelete = { deleteTarget = u }
                    )
                }
            }
            // pager
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { if (state.page > 1) vm.load(state.page - 1) },
                    enabled = state.page > 1
                ) { Text("上一页") }
                Text(
                    "${state.page} / ${state.pages}",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                TextButton(
                    onClick = { if (state.page < state.pages) vm.load(state.page + 1) },
                    enabled = state.page < state.pages
                ) { Text("下一页") }
            }
        }
    }
}

@Composable
private fun UserRow(user: UserRowDto, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(user.username, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(0.dp))
                Box(
                    Modifier.padding(start = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (user.role == "admin") "管理员" else "用户",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (user.role == "admin") DsoColors.AppRed else DsoColors.HighlightBlue,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                (if (user.role == "admin") DsoColors.AppRed else DsoColors.HighlightBlue).copy(alpha = 0.1f)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                "创建时间：" + TimeFormat.formatDateTime(user.createdAt),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onEdit) { Text("编辑", color = DsoColors.HighlightBlue) }
        TextButton(onClick = onDelete) { Text("删除", color = DsoColors.AppRed) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
        title = { Text(title) },
        text = {
            Column {
                AppOutlinedTextField(
                    value = username, onValueChange = { username = it },
                    label = { Text("用户名") }, singleLine = true,
                    enabled = usernameEditable, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                AppOutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text(if (usernameEditable) "密码（至少 6 位）" else "新密码（留空则不修改）") },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                ExposedDropdownMenuBox(expanded = roleExpanded, onExpandedChange = { roleExpanded = it }) {
                    AppOutlinedTextField(
                        value = if (role == "admin") "管理员 (admin)" else "普通用户 (user)",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("角色") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = roleExpanded, onDismissRequest = { roleExpanded = false }) {
                        listOf("user" to "普通用户 (user)", "admin" to "管理员 (admin)").forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { role = value; roleExpanded = false }
                            )
                        }
                    }
                }
                localError?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = DsoColors.AppRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    usernameEditable && username.isBlank() -> localError = "用户名不能为空"
                    usernameEditable && password.length < 6 -> localError = "密码至少 6 位"
                    else -> onSubmit(username, password, role)
                }
            }) { Text("保存", color = DsoColors.AppRed) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("取消") } }
    )
}
