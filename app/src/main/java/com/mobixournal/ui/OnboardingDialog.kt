package com.mobixournal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Onboarding dialog presented on the first launch of the app.
 *
 * Walks the user through four core aspects of the app:
 * 1. Stylus setup: pressure sensitivity, barrel-button shortcuts, and palm rejection.
 * 2. Shape sizing & pressure sensitivity: explains why shapes have uniform thickness
 *    and how to match or balance them with freehand pen strokes in Settings.
 * 3. Graphics tablet support: using hardware tablets and assigning tool shortcuts.
 * 4. Handedness: which vertical edge the Main Toolbar docks to (left by default).
 *
 * Step 4 edits [AppSettings.toolbarPosition] through [onToolbarPosition], so the choice takes effect
 * while the dialog is still up — the rail visibly moves behind it.
 */
@Composable
fun OnboardingDialog(
    onDismiss: () -> Unit,
    toolbarPosition: ToolbarPosition = ToolbarPosition.LEFT,
    onToolbarPosition: (ToolbarPosition) -> Unit = {},
) {
    var step by remember { mutableIntStateOf(1) }
    val totalSteps = 4

    BackHandler(enabled = true) {
        if (step > 1) step-- else onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(0.92f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                OnboardingHeader(step = step, totalSteps = totalSteps)

                Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                    when (step) {
                        1 -> OnboardingStep1Stylus()
                        2 -> OnboardingStep2Figures()
                        3 -> OnboardingStep3Tablet()
                        4 -> OnboardingStep4Handedness(toolbarPosition, onToolbarPosition)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                OnboardingNavigationButtons(
                    step = step,
                    totalSteps = totalSteps,
                    onBack = { if (step > 1) step-- },
                    onNext = { if (step < totalSteps) step++ else onDismiss() },
                    onSkip = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun OnboardingHeader(step: Int, totalSteps: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "MX",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "MobiXournal • Welcome",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 1..totalSteps) {
                Box(
                    modifier = Modifier
                        .height(6.dp)
                        .width(if (i == step) 22.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (i == step) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                        ),
                )
            }
        }
    }
}

@Composable
private fun OnboardingStep1Stylus() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Configure Your Pen & Stylus",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Text(
            text = "MobiXournal is designed for natural handwriting and drawing. In Settings you can tune:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OnboardingFeatureRow(
            icon = Icons.Default.Speed,
            title = "Pressure sensitivity",
            description = "Minimum pressure floor and multiplier to modulate natural stroke thickness.",
            iconTint = MaterialTheme.colorScheme.primary,
        )

        OnboardingFeatureRow(
            icon = Icons.Default.TouchApp,
            title = "Barrel buttons & double-tap",
            description = "Assign quick actions (e.g. toggle eraser or undo) to the stylus barrel button or double-tap.",
            iconTint = MaterialTheme.colorScheme.primary,
        )

        OnboardingFeatureRow(
            icon = Icons.Default.PanTool,
            title = "Palm rejection & hover preview",
            description = "Rest your palm freely on the screen: only the stylus draws, while fingers pan and zoom.",
            iconTint = MaterialTheme.colorScheme.primary,
        )

        Text(
            text = "All these options are in Settings → Stylus.",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun OnboardingStep2Figures() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Category,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Harmonize Figures & Pen Strokes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Text(
            text = "Geometric figures (lines, rectangles, ellipses) are drawn at their nominal fixed width without pressure sensitivity.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "How to harmonize geometric figures with handwriting:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Text(
                    text = "With pressure sensitivity enabled, freehand pen strokes taper under light pressure, making figures look visually thicker. You can balance them in Settings:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = "• In Settings → Figures: choose a smaller default slot (e.g. S or M) to match the average thickness of your handwriting.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = "• Or in Settings → Stylus: turn off pressure sensitivity if you prefer pen strokes and figures to have the exact same uniform thickness.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            text = "💡 Tip: you can also adjust the stroke width anytime from the Colour & size pop-up on the tool rail.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OnboardingStep3Tablet() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Graphics Tablets & Shortcuts",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Text(
            text = "If you connect an external graphics tablet (USB/Bluetooth with ExpressKeys) or a hardware keyboard, you can streamline every action:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OnboardingFeatureRow(
            icon = Icons.Default.Palette,
            title = "One key for every tool and colour",
            description = "Instantly switch to pen, highlighter, eraser, selection tools, figures, or individual palette colours.",
            iconTint = MaterialTheme.colorScheme.primary,
        )

        OnboardingFeatureRow(
            icon = Icons.Default.TouchApp,
            title = "1-click key detection",
            description = "In Settings → Shortcuts, tap the keyboard detection icon and press any physical button on your tablet to assign it in one click (manual key entry is also supported).",
            iconTint = MaterialTheme.colorScheme.primary,
        )

        Text(
            text = "Configurable anytime in Settings → Shortcuts.",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun OnboardingFeatureRow(
    icon: ImageVector,
    title: String,
    description: String,
    iconTint: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.padding(top = 2.dp).size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OnboardingNavigationButtons(
    step: Int,
    totalSteps: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (step > 1) {
            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Back")
            }
        } else {
            Spacer(Modifier.width(1.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (step < totalSteps) {
                TextButton(onClick = onSkip) {
                    Text(
                        "Skip",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
            }

            Button(
                onClick = onNext,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                if (step == totalSteps) {
                    Text("Get started")
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                } else {
                    Text("Next")
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
