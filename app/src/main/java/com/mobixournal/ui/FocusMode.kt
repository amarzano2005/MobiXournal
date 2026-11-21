package com.mobixournal.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.mobixournal.ui.theme.DialogPresence
import com.mobixournal.ui.theme.LocalFloatingSurfaceClosing
import com.mobixournal.ui.theme.floatingRim
import com.mobixournal.ui.theme.liquidGlassSpringAnimation
import com.mobixournal.ui.theme.rememberChromeInk
import com.mobixournal.ui.theme.rememberGlassSurfaceColor

/**
 * Coordinates Android system bars and canvas relayout when Focus mode (`fullPage`) toggles.
 *
 * Hides status and navigation bars using transient swipe reveal while active, restores them on exit
 * or disposal, and requests a canvas repaint after layout settles so the page fits the expanded viewport.
 */
@Composable
internal fun FocusModeEffect(active: Boolean, onRepaint: () -> Unit) {
    val view = LocalView.current
    val context = LocalContext.current
    val currentOnRepaint by rememberUpdatedState(onRepaint)

    DisposableEffect(active, view) {
        val window = context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (controller != null) {
            if (active) {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            if (active && controller != null) {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (!settled) {
            settled = true
            return@LaunchedEffect
        }
        withFrameNanos { }
        withFrameNanos { }
        currentOnRepaint()
    }
}

/**
 * Floating frosted-glass pill shown while Focus mode is active so touch/stylus users can exit
 * immersive view with a single tap without relying on hardware keys or the system Back gesture.
 */
@Composable
internal fun FocusModeExitButton(
    visible: Boolean,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    val glassColor = rememberGlassSurfaceColor()
    val ink = rememberChromeInk()
    Box(modifier = modifier.statusBarsPadding().padding(12.dp)) {
        DialogPresence(visible) {
            Surface(
                shape = shape,
                color = glassColor,
                tonalElevation = 0.dp,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .liquidGlassSpringAnimation(
                        expanded = !LocalFloatingSurfaceClosing.current,
                        origin = TransformOrigin(1f, 0f),
                    )
                    .floatingRim(18.dp)
                    .clip(shape)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Exit focus mode"
                    }
                    .clickable(onClick = onExit),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FullscreenExit,
                        contentDescription = null,
                        tint = ink,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Exit focus",
                        style = MaterialTheme.typography.labelMedium,
                        color = ink,
                    )
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
