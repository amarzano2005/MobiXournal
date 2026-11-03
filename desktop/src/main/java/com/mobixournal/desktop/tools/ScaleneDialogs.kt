package com.mobixournal.desktop.tools
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.settings.DesktopTheme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobixournal.render.ShapeBuilder
import com.mobixournal.render.TrapezoidKind
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Dialog to customize the interior angles of a **scalene triangle**, mirroring the mobile editor.
 * The three angles must sum to 180°, and each angle must be at least 5°.
 */
@Composable
internal fun ScaleneTriangleDialog(
    angleA: Double,
    angleB: Double,
    angleC: Double,
    onConfirm: (Double, Double, Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var a by remember { mutableStateOf(angleA.roundToInt().toFloat()) }
    var b by remember { mutableStateOf(angleB.roundToInt().toFloat()) }
    var c by remember { mutableStateOf(angleC.roundToInt().toFloat()) }

    val sum = a.roundToInt() + b.roundToInt() + c.roundToInt()
    val isValid = sum == 180 && a >= 5f && b >= 5f && c >= 5f

    ScaleneDialogFrame(
        title = "Scalene triangle angles",
        subtitle = "Set the three interior angles (sum must equal 180°).",
        isValid = isValid,
        onDismiss = onDismiss,
        onSave = { onConfirm(a.toDouble(), b.toDouble(), c.toDouble()) },
    ) {
        TrianglePreviewCanvas(a, b, c)
        Spacer(Modifier.height(10.dp))
        ScaleneAngleSliderRow("Angle A (α)", a) { a = it }
        ScaleneAngleSliderRow("Angle B (β)", b) { b = it }
        ScaleneAngleSliderRow("Angle C (γ)", c) { c = it }

        Spacer(Modifier.height(6.dp))
        TriangleSumRow(sum, isValid, a, b) { c = it }

        Spacer(Modifier.height(8.dp))
        Text("Presets:", color = DesktopTheme.inkSecondary, style = DesktopTheme.label)
        Spacer(Modifier.height(4.dp))
        TrianglePresetsRow { pa, pb, pc -> a = pa; b = pb; c = pc }
    }
}

/**
 * Dialog to customize base angles of a **scalene trapezoid**, mirroring the mobile editor.
 */
@Composable
internal fun ScaleneTrapezoidDialog(
    angleA: Double,
    angleB: Double,
    onConfirm: (Double, Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var a by remember { mutableStateOf(angleA.roundToInt().toFloat()) }
    var b by remember { mutableStateOf(angleB.roundToInt().toFloat()) }

    ScaleneDialogFrame(
        title = "Scalene trapezoid angles",
        subtitle = "Set the two angles the legs make with the longer base (left and right).",
        isValid = true,
        onDismiss = onDismiss,
        onSave = { onConfirm(a.toDouble(), b.toDouble()) },
    ) {
        TrapezoidPreviewCanvas(a.toDouble(), b.toDouble())
        Spacer(Modifier.height(10.dp))
        val trapRange = ShapeBuilder.MIN_TRAPEZOID_ANGLE_DEG.toFloat()..ShapeBuilder.MAX_TRAPEZOID_ANGLE_DEG.toFloat()
        ScaleneAngleSliderRow("Left angle (α)", a, trapRange) { a = it }
        ScaleneAngleSliderRow("Right angle (β)", b, trapRange) { b = it }

        Spacer(Modifier.height(8.dp))
        Text("Presets:", color = DesktopTheme.inkSecondary, style = DesktopTheme.label)
        Spacer(Modifier.height(4.dp))
        TrapezoidPresetsRow { pa, pb -> a = pa; b = pb }
    }
}

@Composable
private fun ScaleneDialogFrame(
    title: String,
    subtitle: String,
    isValid: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .clickable(enabled = false) {}
                .glassSpring()
                .glassSurface()
                .padding(18.dp),
        ) {
            Text(title, color = DesktopTheme.ink, style = DesktopTheme.title)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = DesktopTheme.inkSecondary, style = DesktopTheme.body)
            Spacer(Modifier.height(12.dp))
            content()
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                PanelButton(label = "Cancel", primary = false, enabled = true, onClick = onDismiss)
                Spacer(Modifier.width(8.dp))
                PanelButton(label = "Save", primary = true, enabled = isValid, onClick = onSave)
            }
        }
    }
}

@Composable
private fun TrianglePreviewCanvas(a: Float, b: Float, c: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .background(DesktopTheme.activeSlot, RoundedCornerShape(8.dp))
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val total = a + b + c
            if (total <= 0f) return@Canvas
            val radA = Math.toRadians((a * 180.0 / total))
            val radB = Math.toRadians((b * 180.0 / total))
            val radC = Math.PI - radA - radB
            val sideC = sin(radC)
            val sideB = sin(radB)
            val vx0 = 0.0
            val vy0 = 0.0
            val vx1 = sideC
            val vy1 = 0.0
            val vx2 = sideB * cos(radA)
            val vy2 = sideB * sin(radA)

            val minX = minOf(vx0, vx1, vx2)
            val maxX = maxOf(vx0, vx1, vx2)
            val minY = minOf(vy0, vy1, vy2)
            val maxY = maxOf(vy0, vy1, vy2)
            val triW = (maxX - minX).coerceAtLeast(0.001)
            val triH = (maxY - minY).coerceAtLeast(0.001)

            val scale = minOf((size.width * 0.8) / triW, (size.height * 0.8) / triH)
            val offsetX = (size.width - triW * scale) / 2.0 - minX * scale
            val offsetY = (size.height - triH * scale) / 2.0 + maxY * scale

            fun px(x: Double) = (x * scale + offsetX).toFloat()
            fun py(y: Double) = (offsetY - y * scale).toFloat()

            val p = Path().apply {
                moveTo(px(vx0), py(vy0))
                lineTo(px(vx1), py(vy1))
                lineTo(px(vx2), py(vy2))
                close()
            }
            drawPath(p, color = DesktopTheme.accent.copy(alpha = 0.18f))
            drawPath(p, color = DesktopTheme.accent, style = Stroke(width = 2.dp.toPx()))
        }
    }
}

@Composable
private fun TrapezoidPreviewCanvas(a: Double, b: Double) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .background(DesktopTheme.activeSlot, RoundedCornerShape(8.dp))
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pts = ShapeBuilder.trapezoidOutline(TrapezoidKind.SCALENE, 100.0, 100.0, a, b)
            if (pts.size < 3) return@Canvas
            val minX = pts.minOf { it.first }
            val maxX = pts.maxOf { it.first }
            val minY = pts.minOf { it.second }
            val maxY = pts.maxOf { it.second }
            val figW = (maxX - minX).coerceAtLeast(1e-6)
            val figH = (maxY - minY).coerceAtLeast(1e-6)
            val scale = minOf((size.width * 0.8) / figW, (size.height * 0.8) / figH)
            val offsetX = (size.width - figW * scale) / 2.0 - minX * scale
            val offsetY = (size.height - figH * scale) / 2.0 - minY * scale
            val p = Path().apply {
                moveTo((pts[0].first * scale + offsetX).toFloat(), (pts[0].second * scale + offsetY).toFloat())
                for (i in 1 until pts.size) {
                    lineTo((pts[i].first * scale + offsetX).toFloat(), (pts[i].second * scale + offsetY).toFloat())
                }
                close()
            }
            drawPath(p, color = DesktopTheme.accent.copy(alpha = 0.18f))
            drawPath(p, color = DesktopTheme.accent, style = Stroke(width = 2.dp.toPx()))
        }
    }
}

@Composable
private fun TriangleSumRow(sum: Int, isValid: Boolean, a: Float, b: Float, onBalanceC: (Float) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = if (isValid) "Sum: $sum° ✓" else "Sum: $sum° (must be 180°)",
            color = if (isValid) DesktopTheme.accent else DesktopTheme.danger,
            style = DesktopTheme.body,
        )
        if (!isValid) {
            val neededC = (180 - a.roundToInt() - b.roundToInt()).toFloat()
            if (neededC in 5f..170f) {
                Text(
                    "Auto-balance C (${neededC.roundToInt()}°)",
                    color = DesktopTheme.accent,
                    style = DesktopTheme.label,
                    modifier = Modifier.clickable { onBalanceC(neededC) }.padding(4.dp),
                )
            }
        }
    }
}

@Composable
private fun TrianglePresetsRow(onPick: (Float, Float, Float) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        val presets = listOf(
            Triple(40f, 60f, 80f),
            Triple(30f, 60f, 90f),
            Triple(45f, 60f, 75f),
            Triple(35f, 55f, 90f),
            Triple(25f, 45f, 110f),
        )
        for ((pa, pb, pc) in presets) {
            PresetChip("${pa.roundToInt()}°-${pb.roundToInt()}°-${pc.roundToInt()}°") {
                onPick(pa, pb, pc)
            }
        }
    }
}

@Composable
private fun TrapezoidPresetsRow(onPick: (Float, Float) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        val presets = listOf(75f to 60f, 60f to 60f, 120f to 60f)
        for ((pa, pb) in presets) {
            PresetChip("${pa.roundToInt()}°-${pb.roundToInt()}°") {
                onPick(pa, pb)
            }
        }
    }
}

@Composable
private fun ScaleneAngleSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float> = 5f..170f,
    onValueChange: (Float) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().height(36.dp),
    ) {
        Text(label, color = DesktopTheme.ink, style = DesktopTheme.body, modifier = Modifier.width(96.dp))
        StepButton("−", enabled = value > range.start) {
            onValueChange((value - 1f).coerceIn(range))
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = { onValueChange(it.roundToInt().toFloat()) },
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = DesktopTheme.accent,
                activeTrackColor = DesktopTheme.accent,
                inactiveTrackColor = DesktopTheme.inkMuted.copy(alpha = 0.3f),
            ),
        )
        StepButton("+", enabled = value < range.endInclusive) {
            onValueChange((value + 1f).coerceIn(range))
        }
        Text(
            "${value.roundToInt()}°",
            color = DesktopTheme.ink,
            style = DesktopTheme.numeric,
            modifier = Modifier.width(36.dp),
        )
    }
}

@Composable
private fun StepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .background(
                if (enabled) DesktopTheme.activeSlot else Color.Transparent,
                RoundedCornerShape(4.dp),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) DesktopTheme.ink else DesktopTheme.inkMuted, style = DesktopTheme.body)
    }
}

@Composable
private fun PresetChip(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(DesktopTheme.activeSlot, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = DesktopTheme.ink, style = DesktopTheme.label)
    }
}
