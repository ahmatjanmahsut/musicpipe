package com.musicpipe.player.ui.car

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicpipe.player.di.LocalAppContainer
import com.musicpipe.player.media.car.CarLyricsFormatter
import com.musicpipe.player.media.car.CarLyricsLayout
import com.musicpipe.player.media.car.CarLyricsMode
import com.musicpipe.player.media.car.CarLyricsSettings
import com.musicpipe.player.ui.design.AwwLabel
import com.musicpipe.player.ui.design.AwwMono
import com.musicpipe.player.ui.design.AwwPageHeader
import com.musicpipe.player.ui.design.AwwRule
import com.musicpipe.player.ui.theme.AwwColors
import com.musicpipe.player.ui.theme.AwwSpace
import com.musicpipe.player.ui.theme.AwwTheme

/**
 * 车载歌词设置页（v1.16.0）。
 *
 * 这一页最重要的不是控件，而是**把不确定的东西摊开给用户看**：
 * 蓝牙协议没有歌词字段，能不能在车机屏幕上看到歌词，取决于车机播放页读的是
 * 标题行/歌手行/专辑行中的哪一行。所以这里给出「字段位置」三选一 + 实时预览，
 * 用户在车里试一次就能定下来，而不是被一个"已开启"的开关忽悠。
 */
@Composable
fun CarLyricsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val repo = container.carLyricsRepository
    val ui by repo.state.collectAsStateWithLifecycle()
    val s = ui.settings

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AwwPageHeader(
            index = "08",
            label = "CAR · AVRCP",
            title = "车载歌词",
            meta = if (ui.active) "正在向车机推送歌词行" else "未推送（车机显示真实歌名）",
            onBack = onBack
        )

        // ---- 01 状态 ----
        SectionTitle("01", "当前状态")
        InfoRow("输出设备", ui.output.kind.label + (if (ui.output.name.isNotBlank()) " · " + ui.output.name else ""))
        InfoRow("判定为车机", if (ui.output.isCar) "是" else "否（可切「始终开启」强制推送）")
        InfoRow("歌词行字段", s.layout.label)
        InfoRow("当前推送内容", ui.line ?: "—")
        Row(
            Modifier.fillMaxWidth().padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s3),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AwwLabel(
                text = "重新检测输出设备 →",
                color = AwwColors.Accent,
                modifier = Modifier.clickable { repo.refreshOutput() }
            )
        }

        // 预览：车机那三行现在会是什么
        Box(Modifier.padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s2)) {
            val preview = CarLyricsFormatter.fields(
                songTitle = "示例歌名",
                songArtist = "示例歌手",
                songAlbum = "示例专辑",
                line = ui.line ?: "（此处会显示当前歌词行）",
                layout = s.layout,
                maxChars = s.maxChars
            )
            Column(Modifier.fillMaxWidth().background(AwwColors.Paper2, com.musicpipe.player.ui.theme.AwwShapes.Square).padding(AwwSpace.s3)) {
                AwwMono(text = "车机预计显示", color = AwwColors.Ink3)
                Spacer(Modifier.height(AwwSpace.s1))
                Text(preview.title, style = AwwTheme.type.body, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                Text(preview.artist, style = AwwTheme.type.bodySmall, color = AwwColors.Ink2, maxLines = 1)
                Text(preview.album, style = AwwTheme.type.bodySmall, color = AwwColors.Ink3, maxLines = 1)
            }
        }
        AwwRule()

        // ---- 02 模式 ----
        SectionTitle("02", "开启方式")
        CarLyricsMode.entries.forEach { mode ->
            ChoiceRow(
                label = mode.label,
                selected = s.mode == mode,
                onClick = { repo.setMode(mode) }
            )
        }
        AwwRule()

        // ---- 03 字段位置 ----
        SectionTitle("03", "歌词写进哪一行")
        CarLyricsLayout.entries.forEach { layout ->
            ChoiceRow(
                label = layout.label,
                selected = s.layout == layout,
                onClick = { repo.setLayout(layout) }
            )
        }
        Hint(
            "蓝牙（AVRCP）只传歌名/歌手/专辑三行文本，没有「歌词」字段。" +
                "多数车机播放页只画标题与歌手两行，所以默认用标题行；" +
                "如果换了字段车机仍然不显示歌词，说明它只读专辑行，逐个试即可。"
        )
        AwwRule()

        // ---- 04 延迟补偿 ----
        SectionTitle("04", "延迟补偿")
        IntSlider(
            label = "提前",
            value = s.offsetMs.toInt(),
            range = -1000..1000,
            step = 50,
            formatter = { v -> (if (v >= 0) "提前 " else "延后 ") + kotlin.math.abs(v).toString() + " ms" },
            onCommit = { repo.setOffsetMs(it.toLong()) }
        )
        Hint(
            "蓝牙从手机到车机出声通常有 150~400ms 延迟。歌词总是「早一点到」，" +
                "把这条往右调，让车机屏幕上那行正好对上耳朵听到的那句。"
        )
        AwwRule()

        // ---- 05 单行字数 ----
        SectionTitle("05", "单行长度")
        IntSlider(
            label = "字数",
            value = s.maxChars,
            range = CarLyricsSettings.MIN_CHARS..CarLyricsSettings.MAX_CHARS,
            step = 4,
            formatter = { v -> v.toString() + " 字" },
            onCommit = { repo.setMaxChars(it) }
        )
        Hint(
            "部分车机对超长行会直接不显示（而不是滚动）。截太短又会被切碎，" +
                "默认 48 字是「一整句中文」的经验值。"
        )
        AwwRule()

        // ---- 说明 ----
        Column(Modifier.padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s4)) {
            Text(
                "说明：歌词行是通过媒体元数据（标题/歌手/专辑）送到车机的，" +
                    "所以开启期间手机的通知栏/锁屏也会显示这行歌词 —— 它们读的是同一份元数据。",
                style = AwwTheme.type.bodySmall,
                color = AwwColors.Ink3
            )
            Spacer(Modifier.height(AwwSpace.s2))
            Text(
                "歌词来自服务端已匹配的歌词（与 App 歌词页同一份缓存），没有匹配到歌词的歌不会推送任何内容，" +
                    "车机仍然显示真实歌名与歌手。",
                style = AwwTheme.type.bodySmall,
                color = AwwColors.Ink3
            )
        }
        Spacer(Modifier.height(AwwSpace.s16))
    }
}

@Composable
private fun SectionTitle(number: String, text: String) {
    Spacer(Modifier.height(AwwSpace.s8))
    Row(
        Modifier.fillMaxWidth().padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AwwMono(text = number, color = AwwColors.Accent)
        Spacer(Modifier.width(AwwSpace.s3))
        AwwLabel(text = text, color = AwwColors.Ink3)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AwwLabel(text = label, color = AwwColors.Ink3)
        Spacer(Modifier.weight(1f))
        Text(value, style = AwwTheme.type.bodySmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AwwLabel(text = label, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.weight(1f))
        if (selected) AwwMono(text = "✓ 已选", color = AwwColors.Accent)
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = AwwTheme.type.bodySmall,
        color = AwwColors.Ink3,
        modifier = Modifier.padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s2)
    )
}

/** 整数滑杆（延迟补偿/字数都要求"整档"，不用浮点）。 */
@Composable
private fun IntSlider(
    label: String,
    value: Int,
    range: IntRange,
    step: Int,
    formatter: (Int) -> String,
    onCommit: (Int) -> Unit
) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s1),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AwwMono(text = label, color = AwwColors.Ink3, modifier = Modifier.width(48.dp))
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onCommit(local.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = ((range.last - range.first) / step).coerceAtLeast(1) - 1,
            colors = SliderDefaults.colors(
                thumbColor = AwwColors.Accent,
                activeTrackColor = MaterialTheme.colorScheme.onSurface,
                inactiveTrackColor = AwwColors.Line
            ),
            modifier = Modifier.weight(1f)
        )
        AwwMono(text = formatter(local.toInt()), color = AwwColors.Ink2, modifier = Modifier.width(86.dp))
    }
}
