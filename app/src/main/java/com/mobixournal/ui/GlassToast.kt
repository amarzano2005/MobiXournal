package com.mobixournal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mobixournal.ui.theme.rememberFloatingSurfaceColor
import kotlinx.coroutines.delay

/**
 * A sleek, frosted-glass notification pill floating near the bottom of the screen,
 * styled consistently with the modern translucent chrome.
 */
@Composable
fun GlassToastHost(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(message) {
        if (message != null) {
            delay(2800L)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier.navigationBarsPadding().padding(bottom = 24.dp),
    ) {
        if (message != null) {
            val glassBg = rememberFloatingSurfaceColor().copy(alpha = 0.90f)
            val borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = glassBg,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, borderColor),
                modifier = Modifier.padding(horizontal = 24.dp),
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
