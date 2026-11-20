package com.mobixournal.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.ui.theme.chromeIsDark
import com.mobixournal.ui.theme.floatingRim
import com.mobixournal.ui.theme.rememberChromeInk
import com.mobixournal.ui.theme.rememberGlassSurfaceColor

private enum class SheetTab { DOCUMENT, STUDIO }
private enum class SheetView { DOCUMENT, STUDIO, EXPORT }

private const val COMPACT_SHEET_BREAKPOINT_DP = 600

/**
 * Anchors the Command Sheet to the bottom-center of the window on compact phone screens so the
 * primary controls sit within easy thumb reach.
 */
private class BottomSheetPopupPositionProvider(
    private val bottomMarginPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = ((windowSize.width - popupContentSize.width) / 2).coerceAtLeast(0)
        val y = (windowSize.height - popupContentSize.height - bottomMarginPx).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

@Composable
internal fun rememberSheetSurfaceColor(): Color {
    val isDark = chromeIsDark()
    return rememberChromeInk().copy(alpha = if (isDark) 0.06f else 0.045f)
}

/**
 * EditorTopBar adapter that wires [OverflowMenu] to [EditorUiState], [PaneState], [TabsUiState]
 * and [AppSettings] without inflating `EditorRegions.kt`.
 */
@Composable
internal fun EditorOverflowMenu(
    ui: EditorUiState,
    pane: PaneState,
    stripTabs: TabsUiState,
    settings: AppSettings?,
    onSettingsChange: ((AppSettings) -> Unit)?,
    onOpen: () -> Unit,
    onNewTab: () -> Unit,
    onSave: () -> Unit,
    onImportPdf: () -> Unit,
    onExportPagePng: () -> Unit,
    onExportPageSvg: () -> Unit,
    onSavePortableCopy: () -> Unit,
) {
    OverflowMenu(
        isReadOnly = pane.isReadOnly,
        canSave = stripTabs.canSave,
        documentTitle = stripTabs.titles.getOrNull(stripTabs.activeIndex) ?: "Untitled",
        pageLabel = "Page ${pane.currentPage + 1} of ${pane.pageCount}",
        hasUnsavedChanges = stripTabs.unsaved.getOrNull(stripTabs.activeIndex) == true,
        fullPage = ui.fullPage,
        settings = settings,
        onSelectPenPreset = { preset ->
            if (settings != null && onSettingsChange != null) {
                val updated = settings.copy(
                    selectedPenPresetId = preset.id,
                    minimumPressure = preset.minimumPressure,
                    pressureMultiplier = preset.pressureMultiplier,
                )
                onSettingsChange(updated)
                pane.surface?.applySettings(updated)
            }
        },
        onOpen = onOpen,
        onNewTab = onNewTab,
        onSave = onSave,
        onSaveAs = { ui.showSaveAs = true },
        onImportPdf = onImportPdf,
        onExportPdf = { ui.exportPdfOpen = true },
        onExportPagePng = onExportPagePng,
        onExportPageSvg = onExportPageSvg,
        onSavePortableCopy = onSavePortableCopy,
        onExportAs = { ui.exportAsOpen = true },
        onToggleFullPage = { ui.fullPage = !ui.fullPage },
        onToggleFingerDraws = {
            if (settings != null && onSettingsChange != null) {
                val updated = settings.copy(fingerDraws = !settings.fingerDraws)
                onSettingsChange(updated)
                pane.surface?.applySettings(updated)
            }
        },
        onToggleToolbarPosition = {
            if (settings != null && onSettingsChange != null) {
                val next = if (settings.toolbarPosition == ToolbarPosition.LEFT) {
                    ToolbarPosition.RIGHT
                } else {
                    ToolbarPosition.LEFT
                }
                onSettingsChange(settings.copy(toolbarPosition = next))
            }
        },
        onSettings = { ui.showSettings = true },
        penDiagnostics = ui.penDiagnostics,
        onTogglePenDiagnostics = { ui.penDiagnostics = !ui.penDiagnostics },
        onOpenPenParameters = { ui.showPenParametersDialog = true },
    )
}

/**
 * The top-bar overflow ("hamburger") menu: opens a compact **Command Sheet** with a live document
 * hero card, two clean views (**Document** and **Pen & View**), an inline **Export & Share**
 * drill-down, and a compact footer for **Pen Tuning** and **Settings**.
 *
 * On compact phone widths it anchors at the bottom for one-handed thumb reach; on tablets it
 * anchors as a floating glass popover below the ☰ button.
 */
@Composable
internal fun OverflowMenu(
    isReadOnly: Boolean = false,
    canSave: Boolean = true,
    documentTitle: String = "Untitled",
    pageLabel: String = "",
    hasUnsavedChanges: Boolean = false,
    fullPage: Boolean = false,
    settings: AppSettings?,
    onSelectPenPreset: (PenPreset) -> Unit,
    onOpen: () -> Unit,
    onNewTab: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onImportPdf: () -> Unit,
    onExportPdf: () -> Unit,
    onExportPagePng: () -> Unit,
    onExportPageSvg: () -> Unit,
    onSavePortableCopy: () -> Unit = {},
    onExportAs: () -> Unit = {},
    onToggleFullPage: () -> Unit = {},
    onToggleFingerDraws: () -> Unit = {},
    onToggleToolbarPosition: () -> Unit = {},
    onSettings: () -> Unit,
    penDiagnostics: Boolean,
    onTogglePenDiagnostics: () -> Unit,
    onOpenPenParameters: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(SheetTab.DOCUMENT) }
    var inExport by remember { mutableStateOf(false) }

    val isCompact = LocalConfiguration.current.screenWidthDp < COMPACT_SHEET_BREAKPOINT_DP
    val density = LocalDensity.current
    val bottomProvider = remember(density, isCompact) {
        if (isCompact) BottomSheetPopupPositionProvider(with(density) { 12.dp.roundToPx() }) else null
    }

    Box {
        OverflowTriggerButton(
            expanded = open,
            onClick = {
                inExport = false
                open = true
            },
        )
        ToolbarMenu(
            expanded = open,
            onDismissRequest = {
                open = false
                inExport = false
            },
            positionProvider = bottomProvider,
            modifier = overflowMenuModifier(isCompact),
        ) {
            OverflowSheetBody(
                isCompact = isCompact,
                isReadOnly = isReadOnly,
                documentTitle = documentTitle,
                pageLabel = pageLabel,
                hasUnsavedChanges = hasUnsavedChanges,
                fullPage = fullPage,
                settings = settings,
                penDiagnostics = penDiagnostics,
                activeTab = activeTab,
                inExport = inExport,
                onSelectTab = { tab ->
                    activeTab = tab
                    inExport = false
                },
                onOpenExport = { inExport = true },
                onBackFromExport = { inExport = false },
                onDismiss = {
                    open = false
                    inExport = false
                },
                onNewTab = onNewTab,
                onOpen = onOpen,
                onSaveAs = onSaveAs,
                onImportPdf = onImportPdf,
                onExportPdf = onExportPdf,
                onExportPagePng = onExportPagePng,
                onExportPageSvg = onExportPageSvg,
                onSavePortableCopy = onSavePortableCopy,
                onSelectPenPreset = onSelectPenPreset,
                onToggleFingerDraws = onToggleFingerDraws,
                onToggleFullPage = onToggleFullPage,
                onToggleToolbarPosition = onToggleToolbarPosition,
                onTogglePenDiagnostics = onTogglePenDiagnostics,
                onOpenPenParameters = onOpenPenParameters,
                onSettings = onSettings,
            )
        }
    }
}

@Composable
private fun OverflowTriggerButton(
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val glassColor = rememberGlassSurfaceColor()
    val tint = if (expanded) MaterialTheme.colorScheme.primary else rememberChromeInk()
    Surface(
        shape = CircleShape,
        color = glassColor,
        tonalElevation = 0.dp,
        shadowElevation = 4.dp,
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .floatingRim(18.dp),
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                ChromeGlyph.MENU.imageVector(),
                contentDescription = "Menu",
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun OverflowSheetBody(
    isCompact: Boolean,
    isReadOnly: Boolean,
    documentTitle: String,
    pageLabel: String,
    hasUnsavedChanges: Boolean,
    fullPage: Boolean,
    settings: AppSettings?,
    penDiagnostics: Boolean,
    activeTab: SheetTab,
    inExport: Boolean,
    onSelectTab: (SheetTab) -> Unit,
    onOpenExport: () -> Unit,
    onBackFromExport: () -> Unit,
    onDismiss: () -> Unit,
    onNewTab: () -> Unit,
    onOpen: () -> Unit,
    onSaveAs: () -> Unit,
    onImportPdf: () -> Unit,
    onExportPdf: () -> Unit,
    onExportPagePng: () -> Unit,
    onExportPageSvg: () -> Unit,
    onSavePortableCopy: () -> Unit,
    onSelectPenPreset: (PenPreset) -> Unit,
    onToggleFingerDraws: () -> Unit,
    onToggleFullPage: () -> Unit,
    onToggleToolbarPosition: () -> Unit,
    onTogglePenDiagnostics: () -> Unit,
    onOpenPenParameters: () -> Unit,
    onSettings: () -> Unit,
) {
    val currentView = when {
        inExport -> SheetView.EXPORT
        activeTab == SheetTab.STUDIO -> SheetView.STUDIO
        else -> SheetView.DOCUMENT
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (isCompact) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(rememberChromeInk().copy(alpha = 0.22f)),
                )
            }
        }
        DocumentHeroCard(
            title = documentTitle,
            pageLabel = pageLabel,
            hasUnsavedChanges = hasUnsavedChanges,
            isReadOnly = isReadOnly,
            onExportClick = onOpenExport,
        )
        if (!inExport) {
            SheetModeSwitcher(activeTab = activeTab, onSelectTab = onSelectTab)
        }
        OverflowSheetContent(
            currentView = currentView,
            isReadOnly = isReadOnly,
            fullPage = fullPage,
            settings = settings,
            penDiagnostics = penDiagnostics,
            onOpenExport = onOpenExport,
            onBackFromExport = onBackFromExport,
            onDismiss = onDismiss,
            onNewTab = onNewTab,
            onOpen = onOpen,
            onSaveAs = onSaveAs,
            onImportPdf = onImportPdf,
            onExportPdf = onExportPdf,
            onExportPagePng = onExportPagePng,
            onExportPageSvg = onExportPageSvg,
            onSavePortableCopy = onSavePortableCopy,
            onSelectPenPreset = onSelectPenPreset,
            onToggleFingerDraws = onToggleFingerDraws,
            onToggleFullPage = onToggleFullPage,
            onToggleToolbarPosition = onToggleToolbarPosition,
            onTogglePenDiagnostics = onTogglePenDiagnostics,
        )
        SheetFooterRow(
            onPenTuning = { onDismiss(); onOpenPenParameters() },
            onSettings = { onDismiss(); onSettings() },
        )
    }
}

@Composable
private fun OverflowSheetContent(
    currentView: SheetView,
    isReadOnly: Boolean,
    fullPage: Boolean,
    settings: AppSettings?,
    penDiagnostics: Boolean,
    onOpenExport: () -> Unit,
    onBackFromExport: () -> Unit,
    onDismiss: () -> Unit,
    onNewTab: () -> Unit,
    onOpen: () -> Unit,
    onSaveAs: () -> Unit,
    onImportPdf: () -> Unit,
    onExportPdf: () -> Unit,
    onExportPagePng: () -> Unit,
    onExportPageSvg: () -> Unit,
    onSavePortableCopy: () -> Unit,
    onSelectPenPreset: (PenPreset) -> Unit,
    onToggleFingerDraws: () -> Unit,
    onToggleFullPage: () -> Unit,
    onToggleToolbarPosition: () -> Unit,
    onTogglePenDiagnostics: () -> Unit,
) {
    AnimatedContent(
        targetState = currentView,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "OverflowSheetView",
    ) { view ->
        when (view) {
            SheetView.DOCUMENT -> DocumentTabView(
                isReadOnly = isReadOnly,
                onNewTab = { onDismiss(); onNewTab() },
                onOpen = { onDismiss(); onOpen() },
                onSaveAs = { onDismiss(); onSaveAs() },
                onImportPdf = { onDismiss(); onImportPdf() },
                onOpenExport = onOpenExport,
            )
            SheetView.STUDIO -> StudioTabView(
                settings = settings,
                fullPage = fullPage,
                penDiagnostics = penDiagnostics,
                onSelectPenPreset = { preset ->
                    onSelectPenPreset(preset)
                    onDismiss()
                },
                onToggleFingerDraws = onToggleFingerDraws,
                onToggleFullPage = {
                    onToggleFullPage()
                    onDismiss()
                },
                onToggleToolbarPosition = onToggleToolbarPosition,
                onTogglePenDiagnostics = {
                    onTogglePenDiagnostics()
                    onDismiss()
                },
            )
            SheetView.EXPORT -> ExportDrilldownView(
                isReadOnly = isReadOnly,
                onBack = onBackFromExport,
                onExportPdf = { onDismiss(); onExportPdf() },
                onExportPng = { onDismiss(); onExportPagePng() },
                onExportSvg = { onDismiss(); onExportPageSvg() },
                onSavePortable = { onDismiss(); onSavePortableCopy() },
            )
        }
    }
}

@Composable
private fun DocumentHeroCard(
    title: String,
    pageLabel: String,
    hasUnsavedChanges: Boolean,
    isReadOnly: Boolean,
    onExportClick: () -> Unit,
) {
    val ink = rememberChromeInk()
    val accent = MaterialTheme.colorScheme.primary
    val formatBadge = remember(title, isReadOnly) {
        when {
            isReadOnly -> "Read-only"
            title.endsWith(".xopp", ignoreCase = true) -> ".xopp"
            title.endsWith(".pdf", ignoreCase = true) -> ".pdf"
            else -> ".mobix"
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(rememberSheetSurfaceColor())
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniPageThumbnail(ink = ink, accent = accent)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (hasUnsavedChanges) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(accent),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = formatBadge,
                    style = MaterialTheme.typography.labelSmall,
                    color = ink.copy(alpha = 0.78f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ink.copy(alpha = 0.10f))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
                if (pageLabel.isNotEmpty()) {
                    Text(
                        text = pageLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = ink.copy(alpha = 0.58f),
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.14f))
                .clickable(enabled = !isReadOnly, onClick = onExportClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                ChromeGlyph.EXPORT.imageVector(),
                contentDescription = "Export and share",
                tint = if (isReadOnly) ink.copy(alpha = 0.35f) else accent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun MiniPageThumbnail(ink: Color, accent: Color) {
    Canvas(
        modifier = Modifier
            .size(width = 34.dp, height = 42.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(ink.copy(alpha = 0.08f)),
    ) {
        val ruleColor = ink.copy(alpha = 0.16f)
        val strokeColor = accent.copy(alpha = 0.85f)
        for (i in 1..3) {
            val y = size.height * (i * 0.24f)
            drawLine(ruleColor, Offset(5f, y), Offset(size.width - 5f, y), strokeWidth = 1.2f)
        }
        val path = Path().apply {
            moveTo(size.width * 0.18f, size.height * 0.62f)
            cubicTo(
                size.width * 0.38f, size.height * 0.25f,
                size.width * 0.62f, size.height * 0.78f,
                size.width * 0.82f, size.height * 0.42f,
            )
        }
        drawPath(path, strokeColor, style = Stroke(width = 2.4f, cap = StrokeCap.Round))
    }
}

@Composable
private fun SheetModeSwitcher(
    activeTab: SheetTab,
    onSelectTab: (SheetTab) -> Unit,
) {
    val ink = rememberChromeInk()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(rememberSheetSurfaceColor())
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SheetTabPill(
            label = "Document",
            icon = ChromeGlyph.PDF.imageVector(),
            selected = activeTab == SheetTab.DOCUMENT,
            ink = ink,
            onClick = { onSelectTab(SheetTab.DOCUMENT) },
            modifier = Modifier.weight(1f),
        )
        SheetTabPill(
            label = "Pen & View",
            icon = Icons.Outlined.Draw,
            selected = activeTab == SheetTab.STUDIO,
            ink = ink,
            onClick = { onSelectTab(SheetTab.STUDIO) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SheetTabPill(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    ink: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pillBg = if (selected) ink.copy(alpha = 0.14f) else Color.Transparent
    val contentColor = if (selected) ink else ink.copy(alpha = 0.58f)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(pillBg)
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor,
        )
    }
}

private fun overflowMenuModifier(isCompact: Boolean): Modifier = if (isCompact) {
    Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp)
        .widthIn(max = 420.dp)
        .navigationBarsPadding()
} else {
    Modifier.widthIn(min = 312.dp, max = 340.dp)
}
