package com.mobixournal.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * The window's own colour panel: a saturation/value square, a hue slider, a two-way `#RRGGBB` field and a
 * live preview, in the app's floating glass rather than a Material dialog.
 *
 * It is the desktop's answer to the tablet's `CustomColorPickerDialog`, not a port of it: that composable
 * lives in the app's `ui/` package and reads `AppSettings`, neither of which exists here. What both must
 * agree on is the *rule* — opaque colours, a strict `#RRGGBB` field, and an invalid entry that keeps the
 * last valid colour and says so ([ColourPicker], [ColourDraft]) — and that rule is shared from
 * `mobix-core`'s palette vocabulary.
 *
 * The panel is a **transient surface over the page** (`docs/ui-modernization.md`): bands never lie on the
 * sheet, a picker that comes and goes may. It is opened by the palette flyout's custom slot and add-colour
 * swatch, and its **Set** hands the colour back to the shell, which owns what the colour *means* (the
 * custom slot, or a new palette entry).
 */
@Composable
internal fun ColourPickerPanel(
    title: String,
    initial: Int,
    onSet: (Int) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember(initial) { mutableStateOf(ColourDraft.fromColour(initial)) }

    Column(
        modifier
            .width(320.dp)
            // The family's arrival: it grows from 82 % on the shared spring and is held on the way out
            // by the shell's GlassPresence, so a picker that comes and goes moves like the tablet's.
            // It is applied **outside** the fill: an alpha layer placed after `background` fades the
            // empty space under the panel, not the panel itself — the first cut of this composed at a
            // full-size, fully opaque panel and the animation was invisible.
            .glassSpring()
            .glassSurface()
            .padding(16.dp),
    ) {
        Text(title, color = DesktopTheme.ink, style = DesktopTheme.title)
        Spacer(Modifier.height(12.dp))
        SaturationValueField(
            draft = draft,
            modifier = Modifier.fillMaxWidth().height(140.dp),
            onChange = { saturation, value -> draft = draft.withSaturation(saturation).withValue(value) },
        )
        Spacer(Modifier.height(12.dp))
        HueSlider(
            hue = draft.hue,
            modifier = Modifier.fillMaxWidth().height(14.dp),
            onChange = { draft = draft.withHue(it) },
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(26.dp)
                    .background(Color(draft.colour), DesktopTheme.capsuleShape)
                    .border(1.dp, DesktopTheme.inkMuted, DesktopTheme.capsuleShape),
            )
            Spacer(Modifier.width(10.dp))
            HexField(draft.text, Modifier.weight(1f)) { draft = draft.withText(it) }
        }
        if (draft.invalid) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Not a #RRGGBB colour — still showing ${ColourPicker.hex(draft.colour)}",
                color = DesktopTheme.danger,
                style = DesktopTheme.label,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PanelButton(label = "Cancel", primary = false, enabled = true, onClick = onCancel)
            Spacer(Modifier.width(8.dp))
            PanelButton(
                label = "Set",
                primary = true,
                // An invalid field has no colour to commit: the last valid one is already the ink, so a
                // live Set would silently do nothing (or worse, commit what was typed before).
                enabled = !draft.invalid,
                onClick = { onSet(draft.colour) },
            )
        }
    }
}

/** Saturation left-to-right over value bottom-to-top, on the hue's own colour, with a ring marker. */
@Composable
private fun SaturationValueField(draft: ColourDraft, modifier: Modifier, onChange: (Float, Float) -> Unit) {
    val hueColour = Color(ColourPicker.hsv(draft.hue, 1f, 1f))
    Canvas(
        modifier
            .clip(DesktopTheme.panelShape)
            .pointerInput(Unit) {
                detectTapGestures { at -> onChange(fractionX(at.x), 1f - fractionY(at.y)) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> onChange(fractionX(change.position.x), 1f - fractionY(change.position.y)) }
            },
    ) {
        drawRect(hueColour)
        drawRect(Brush.horizontalGradient(listOf(Color.White, Color.White.copy(alpha = 0f))))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val centre = Offset(draft.saturation * size.width, (1f - draft.value) * size.height)
        drawCircle(Color.White, radius = 6f, center = centre, style = Stroke(2f))
        drawCircle(Color.Black.copy(alpha = 0.5f), radius = 8f, center = centre, style = Stroke(1f))
    }
}

/** The full hue wheel as one band — the spectrum is the control, not a chrome colour. */
@Composable
private fun HueSlider(hue: Float, modifier: Modifier, onChange: (Float) -> Unit) {
    Canvas(
        modifier
            .clip(DesktopTheme.capsuleShape)
            .pointerInput(Unit) { detectTapGestures { at -> onChange(fractionX(at.x) * 360f) } }
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> onChange(fractionX(change.position.x) * 360f) }
            },
    ) {
        drawRect(
            Brush.horizontalGradient(
                listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red),
            ),
        )
        val x = (hue / 360f) * size.width
        drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), strokeWidth = 3f)
        drawLine(Color.Black.copy(alpha = 0.6f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
    }
}

/** The `#RRGGBB` field: a glass capsule, not a stock text field. */
@Composable
private fun HexField(text: String, modifier: Modifier, onText: (String) -> Unit) {
    BasicTextField(
        value = text,
        onValueChange = onText,
        singleLine = true,
        textStyle = DesktopTheme.body.copy(color = DesktopTheme.ink),
        cursorBrush = SolidColor(DesktopTheme.accent),
        modifier = modifier
            .background(DesktopTheme.activeSlot, DesktopTheme.capsuleShape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

/** `x` as a 0..1 fraction of the node's width (pointer events arrive in pixels). */
private fun androidx.compose.ui.input.pointer.PointerInputScope.fractionX(x: Float): Float =
    (x / size.width.toFloat()).coerceIn(0f, 1f)

/** `y` as a 0..1 fraction of the node's height. */
private fun androidx.compose.ui.input.pointer.PointerInputScope.fractionY(y: Float): Float =
    (y / size.height.toFloat()).coerceIn(0f, 1f)
