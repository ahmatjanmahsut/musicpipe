package com.musicpipe.player.data.image

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.musicpipe.player.ui.theme.DsoColors

/**
 * v1.6.0：封面加载器，专为**滚动列表/网格**设计。
 *
 * 和直接写 AsyncImage 的区别（这是"滑动不流畅"的根因之一）：
 * 1. 显式给出 size —— Coil 会按目标尺寸降采样解码。默认 AsyncImage 若拿不到明确尺寸约束，
 *    会以原图分辨率解码（服务端封面 120KB/上千像素），一屏 6 张就是几十 MB 的位图，
 *    滚动时频繁分配/回收大块 Bitmap 直接触发 GC，表现为"一滑就卡一下"。
 * 2. 关闭 request 级 crossfade：渐变过渡是毫秒级动画 + 每帧重绘，快速滚动时每张新出现的
 *    封面都开一个动画，等同于一屏内开 6 个并行动画 → 掉帧。
 *
 * ★ v1.6.0 布局修复（重要）：
 *   旧实现无条件在传入的 modifier 后追加 `.size(dpSize.dp)`，于是**强制**把绘制尺寸
 *   定型成方形 —— 调用方传进来的 `Modifier.weight(1f).fillMaxHeight()`（2x2 拼图单元格）
 *   或 `Modifier.fillMaxSize()`（整卡封面）全被它覆盖掉，拼图四宫格因此挤成四个正方形，
 *   卡片内出现错位/裁切。现在改为：由调用方决定布局尺寸（`fillMaxSize()` 撑满父容器），
 *   [dpSize] **只用于解码采样** —— 即"按大约多少 dp 显示"告诉 Coil，用来算出合适的
 *   inSampleSize，而不再参与布局。
 * 3. 去掉了 animateFloatAsState 淡入：每个封面一个动画对象 = 滚动时一屏多开 6 个
 *   Animatable + 每帧回调重绘。Coil 自身已按帧驱动 painter 状态，这里直接用 alpha 0/1
 *   切换，滚动期零动画开销。
 */
@Composable
fun FadeInCover(
    model: String?,
    contentDescription: String?,
    dpSize: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val placeholder = modifier.background(DsoColors.CoverPlaceholder)
    if (model.isNullOrBlank()) {
        // 无封面占位也遵循调用方布局（拼图单元格 / 整卡），不再自造正方形。
        Box(placeholder, contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = DsoColors.TextWeak)
        }
        return
    }
    val context = LocalContext.current
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(model)
            // 仅作解码采样提示：让 Coil 解码到 ≈dpSize 的像素，而不是封面原图分辨率。
            .size(dpSize.coerceAtLeast(1))
            .crossfade(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    )
    val loaded = painter.state is AsyncImagePainter.State.Success
    Box(placeholder, contentAlignment = Alignment.Center) {
        if (!loaded) {
            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = DsoColors.TextWeak)
        }
        Image(
            painter = painter,
            contentDescription = contentDescription,
            contentScale = contentScale,
            // 关键：fillMaxSize 让图片跟随父容器（weight/aspectRatio 决定的）实际尺寸，
            // 不再被固定成正方形。alpha 只影响 draw 阶段，不参与测量。
            modifier = Modifier.fillMaxSize().alpha(if (loaded) 1f else 0f)
        )
    }
}
