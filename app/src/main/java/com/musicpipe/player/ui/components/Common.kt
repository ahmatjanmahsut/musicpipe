package com.musicpipe.player.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.musicpipe.player.ui.theme.DsoColors

@Composable
fun LoadingBlock(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = DsoColors.AppRed)
    }
}

/** Inline error/degrade banner (used for lyrics degrade, load failures). */
@Composable
fun ErrorBanner(text: String, modifier: Modifier = Modifier) {
    // v1.10.0（V9）：原来叠了 3 层 padding（16/8 + top4 + Text 内 4），
    // 实际渲染成"上 20 / 下 12 / 左右 20"，与全局 16dp 栅格对不上，
    // 导致带错误横幅的页面比正常页面左右各多缩进 4dp、上下错位。
    // 现在只保留一层 16/8（= 栅格），文本本身不再额外加内边距。
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun EmptyBlock(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
