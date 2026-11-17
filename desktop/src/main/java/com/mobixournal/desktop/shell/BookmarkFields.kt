package com.mobixournal.desktop.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.mobixournal.desktop.settings.DesktopTheme

/** Quiet fields inside the navigation band: focus is an underline, with no capsule lift or glow. */
internal fun Modifier.bookmarkField(focused: Boolean, error: Boolean = false): Modifier =
    background(DesktopTheme.panelField, RoundedCornerShape(4.dp)).drawBehind {
        val ink = when {
            error -> DesktopTheme.danger
            focused -> DesktopTheme.accent
            else -> DesktopTheme.hairline
        }
        val stroke = (if (focused || error) 2.dp else 1.dp).toPx()
        drawLine(ink, Offset(0f, size.height - stroke / 2), Offset(size.width, size.height - stroke / 2), stroke)
    }
