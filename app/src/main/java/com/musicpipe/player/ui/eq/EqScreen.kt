package com.musicpipe.player.ui.eq

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicpipe.player.di.LocalAppContainer
import com.musicpipe.player.media.eq.EqBandSpec
import com.musicpipe.player.media.eq.EqBands
import com.musicpipe.player.media.eq.EqPreset
import com.musicpipe.player.ui.components.ErrorBanner
import com.musicpipe.player.ui.design.AwwButton
import com.musicpipe.player.ui.design.AwwLabel
import com.musicpipe.player.ui.design.AwwMono
import com.musicpipe.player.ui.design.AwwPageHeader
import com.musicpipe.player.ui.design.AwwRule
import com.musicpipe.player.ui.theme.AwwColors
import com.musicpipe.player.ui.theme.AwwShapes
import com.musicpipe.player.ui.theme.AwwSpace
import com.musicpipe.player.ui.theme.AwwTheme

/**
 * 均衡器页（v1.16.0）。
 *
 * 页面结构遵循 DESIGN.md：页眉 → mono 编号小节 → 行 + 1dp 细线，不用卡片、不用阴影、
 * 强调色只出现在"选中"和唯一的主操作上。
 *
 * 交互上最重要的一条：**滑杆拖动只推效果器、不写盘**（松手才 commit）。
 * 见 [EqRepository.previewBand] 的注释 —— 一次拖动几十次回调，每次都写 DataStore
 * 会让手感明显发涩。
 */
@Composable
fun EqScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val repo = container.eqRepository
    val ui by repo.state.collectAsStateWithLifecycle()

    var showSave by remember { mutableStateOf(false) }
    var draftName by remember { mutableStateOf("") }

    if (showSave) {
        AlertDialog(
            onDismissRequest = { showSave = false },
            containerColor = MaterialTheme.colorScheme.background,
            shape = AwwShapes.Square,
            title = { Text("保存为自定义预设", style = AwwTheme.type.h3) },
            text = {
                Column {
                    AwwLabel(text = "预设名称（例如「我的 Mazda 夜间」）", color = AwwColors.Ink3)
                    Spacer(Modifier.height(AwwSpace.s2))
                    Box(Modifier.fillMaxWidth().padding(vertical = AwwSpace.s2)) {
                        BasicTextField(
                            value = draftName,
                            onValueChange = { draftName = it },
                            singleLine = true,
                            textStyle = AwwTheme.type.body.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(AwwColors.Accent),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    AwwRule()
                    Spacer(Modifier.height(AwwSpace.s2))
                    AwwMono(text = "当前曲线 " + curveLabel(ui.gains) + " · 补偿 " + fmtDb(ui.makeupDb) + "dB", color = AwwColors.Ink3)
                }
            },
            confirmButton = {
                AwwLabel(
                    text = "保存", color = AwwColors.Accent,
                    modifier = Modifier
                        .clickable {
                            repo.saveCustomPreset(draftName)
                            showSave = false
                            draftName = ""
                        }
                        .padding(AwwSpace.s3)
                )
            },
            dismissButton = {
                AwwLabel(
                    text = "取消", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { showSave = false }.padding(AwwSpace.s3)
                )
            }
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AwwPageHeader(
            index = "07",
            label = "EFFECTS",
            title = "均衡器",
            meta = (if (ui.enabled) "已开启" else "已关闭") + " · " + ui.presetName,
            onBack = onBack
        )

        // ---- 01 开关 ----
        SectionTitle("01", "开关")
        ChoiceRow(
            label = "启用均衡器",
            value = if (ui.enabled) "开启" else "关闭",
            selected = ui.enabled,
            onClick = { repo.setEnabled(!ui.enabled) }
        )
        ChoiceRow(
            label = "跟随输出设备自动切换",
            value = if (ui.autoSwitch) "开启" else "关闭",
            selected = ui.autoSwitch,
            onClick = { repo.setAutoSwitch(!ui.autoSwitch) }
        )
        AwwMono(
            text = "当前输出：" + ui.output.kind.label +
                (if (ui.output.name.isNotBlank()) " · " + ui.output.name else ""),
            color = AwwColors.Ink3,
            modifier = Modifier.padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s2)
        )
        AwwRule()

        // ---- 02 推荐 ----
        ui.recommendation?.let { rec ->
            Spacer(Modifier.height(AwwSpace.s4))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = AwwSpace.s6),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.width(2.dp).height(28.dp).background(AwwColors.Accent)
                )
                Spacer(Modifier.width(AwwSpace.s3))
                Column(Modifier.weight(1f)) {
                    AwwMono(text = "建议预设 · " + ui.output.kind.label, color = AwwColors.Accent)
                    Text(rec.name + " — " + rec.tagline, style = AwwTheme.type.bodySmall, color = AwwColors.Ink2)
                }
                AwwLabel(
                    text = "应用 →",
                    color = AwwColors.Accent,
                    modifier = Modifier.clickable { repo.applyRecommendation() }.padding(AwwSpace.s2)
                )
            }
            Spacer(Modifier.height(AwwSpace.s3))
            AwwRule()
        }

        // ---- 03 预设 ----
        SectionTitle("02", "预设方案")
        ui.presets.forEach { preset ->
            PresetRow(
                preset = preset,
                selected = preset.id == ui.presetId,
                onSelect = { repo.selectPreset(preset.id) },
                onDelete = if (preset.builtIn) null else ({ repo.deleteCustomPreset(preset.id) })
            )
        }
        AwwRule()

        // ---- 04 手动调节 ----
        SectionTitle("03", "手动调节")
        Box(Modifier.padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s3)) {
            EqCurve(ui.gains, Modifier.fillMaxWidth().height(AwwSpace.s16))
        }
        AwwMono(
            text = "曲线先按 5 段书写，再按设备频段重采样（本机 " +
                (if (ui.runtime.attached) ui.runtime.bandHz.size.toString() + " 段" else "未知") + "）",
            color = AwwColors.Ink3,
            modifier = Modifier.padding(horizontal = AwwSpace.s6)
        )
        Spacer(Modifier.height(AwwSpace.s2))
        EqBands.canonical.forEachIndexed { index, band ->
            BandSlider(
                index = index,
                band = band,
                value = ui.gains.getOrElse(index) { 0f },
                onPreview = { repo.previewBand(index, it) },
                onCommit = { repo.setBand(index, it) }
            )
        }
        BandRowSeparator()
        MakeupSlider(
            value = ui.makeupDb,
            onPreview = { repo.previewMakeup(it) },
            onCommit = { repo.setMakeup(it) }
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s3),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AwwLabel(text = "重置为平直", color = AwwColors.Accent, modifier = Modifier.clickable { repo.resetBands() })
            Spacer(Modifier.weight(1f))
            AwwMono(text = curveLabel(ui.gains), color = AwwColors.Ink3)
        }
        AwwRule()

        // ---- 05 保存 ----
        SectionTitle("04", "自定义预设")
        Box(Modifier.padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s3)) {
            AwwButton(text = "把当前曲线存为预设", onClick = { draftName = ""; showSave = true })
        }
        if (ui.customPresets.isEmpty()) {
            Text(
                "还没有自定义预设。调好曲线后存一次，之后可在上面的列表里一键切回。",
                style = AwwTheme.type.bodySmall,
                color = AwwColors.Ink3,
                modifier = Modifier.padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s2)
            )
        }
        AwwRule()

        // ---- 说明 ----
        if (!ui.runtime.attached && ui.runtime.error != null) {
            ErrorBanner(text = ui.runtime.error ?: "")
        }
        Column(Modifier.padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s4)) {
            Text(
                "均衡器作用在**本机**的音频会话上：声音先在手机里被调过，再通过蓝牙（A2DP）或耳机送出。",
                style = AwwTheme.type.bodySmall,
                color = AwwColors.Ink3
            )
            Spacer(Modifier.height(AwwSpace.s2))
            Text(
                "所以车载/耳机预设不需要车机或耳机支持 EQ —— 它们只是「这类输出常见缺陷的补偿曲线」。" +
                    "增益补偿（右下那条）用来补回削掉的整体响度。",
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

/** 开关/单选行：整行可点，右侧 mono 值 + ✓。 */
@Composable
private fun ChoiceRow(label: String, value: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s4),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AwwLabel(text = label, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.weight(1f))
        AwwMono(text = value, color = if (selected) AwwColors.Accent else AwwColors.Ink3)
        if (selected) {
            Spacer(Modifier.width(AwwSpace.s2))
            AwwMono(text = "✓", color = AwwColors.Accent)
        }
    }
}

@Composable
private fun PresetRow(
    preset: EqPreset,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = preset.name,
                style = AwwTheme.type.body,
                color = if (selected) AwwColors.Accent else MaterialTheme.colorScheme.onSurface
            )
            AwwMono(text = preset.tagline, color = AwwColors.Ink3)
            AwwMono(text = curveLabel(preset.gains), color = AwwColors.Ink3)
        }
        if (selected) AwwMono(text = "✓ 已选", color = AwwColors.Accent)
        if (onDelete != null) {
            Spacer(Modifier.width(AwwSpace.s3))
            AwwLabel(text = "删除", color = AwwColors.Ink3, modifier = Modifier.clickable(onClick = onDelete).padding(AwwSpace.s1))
        }
    }
}

@Composable
private fun BandSlider(
    index: Int,
    band: EqBandSpec,
    value: Float,
    onPreview: (Float) -> Unit,
    onCommit: (Float) -> Unit
) {
    // 本地值驱动滑杆（松手前不回读上游），与播放页 SeekBar 的做法一致。
    var local by remember(index, value) { mutableFloatStateOf(value) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s1),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AwwMono(text = band.label + " Hz", color = AwwColors.Ink3, modifier = Modifier.width(56.dp))
        Slider(
            value = local,
            onValueChange = {
                local = it
                onPreview(it)
            },
            onValueChangeFinished = { onCommit(local) },
            valueRange = EqBands.MIN_DB..EqBands.MAX_DB,
            steps = 47,
            colors = SliderDefaults.colors(
                thumbColor = AwwColors.Accent,
                activeTrackColor = MaterialTheme.colorScheme.onSurface,
                inactiveTrackColor = AwwColors.Line
            ),
            modifier = Modifier.weight(1f)
        )
        AwwMono(
            text = fmtDb(local) + " dB",
            color = if (local == 0f) AwwColors.Ink3 else AwwColors.Accent,
            modifier = Modifier.width(56.dp)
        )
    }
}

@Composable
private fun MakeupSlider(value: Float, onPreview: (Float) -> Unit, onCommit: (Float) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = AwwSpace.s6, vertical = AwwSpace.s1),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AwwMono(text = "补偿", color = AwwColors.Ink3, modifier = Modifier.width(56.dp))
        Slider(
            value = local,
            onValueChange = {
                local = it
                onPreview(it)
            },
            onValueChangeFinished = { onCommit(local) },
            valueRange = 0f..EqBands.MAX_MAKEUP_DB,
            steps = 11,
            colors = SliderDefaults.colors(
                thumbColor = AwwColors.Accent,
                activeTrackColor = MaterialTheme.colorScheme.onSurface,
                inactiveTrackColor = AwwColors.Line
            ),
            modifier = Modifier.weight(1f)
        )
        AwwMono(text = fmtDb(local) + " dB", color = AwwColors.Ink3, modifier = Modifier.width(56.dp))
    }
}

@Composable
private fun BandRowSeparator() {
    Spacer(Modifier.height(AwwSpace.s2))
    AwwRule()
    Spacer(Modifier.height(AwwSpace.s2))
}

/**
 * 曲线预览：一条 0dB 中线 + 每段一根竖线。
 *
 * 刻意不用 animateFloatAsState 做"柱子长起来"的动画 —— 一张静态图已经能说明问题，
 * 而拖动滑杆时每帧都插值反而让人看不清当前值。
 */
@Composable
private fun EqCurve(gains: List<Float>, modifier: Modifier = Modifier) {
    val accent = AwwColors.Accent
    val line = AwwColors.Line
    Canvas(modifier) {
        val mid = size.height / 2f
        val half = size.height / 2f * 0.9f
        drawLine(line, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.dp.toPx())
        val values = EqBands.normalize(gains)
        val step = if (values.size > 1) size.width / (values.size - 1) else size.width
        val stroke = 2.dp.toPx()
        val dot = 2.5.dp.toPx()
        values.forEachIndexed { i, db ->
            val x = step * i
            val y = mid - (db / EqBands.MAX_DB) * half
            drawLine(accent, Offset(x, mid), Offset(x, y), strokeWidth = stroke)
            drawCircle(accent, radius = dot, center = Offset(x, y))
        }
    }
}

private fun fmtDb(v: Float): String {
    val r = Math.round(v * 10f) / 10f
    val body = if (r == r.toInt().toFloat()) r.toInt().toString() else r.toString()
    return if (r > 0f) "+" + body else body
}

private fun curveLabel(gains: List<Float>): String =
    EqBands.canonical.zip(EqBands.normalize(gains)).joinToString(" ") { (spec, db) ->
        spec.label + (if (db > 0f) "+" else "") + fmtDb(db)
    }
