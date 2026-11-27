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
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import com.mobixournal.design.ChromeGlyph
import com.mobixournal.design.DesignTokens
import com.mobixournal.format.SaveFormat
import com.mobixournal.ui.theme.LocalChromeLayoutPolicy
import com.mobixournal.ui.theme.chromeIsDark
import com.mobixournal.ui.theme.floatingRim
import com.mobixournal.ui.theme.liquidGlassFadeTransform
import com.mobixournal.ui.theme.rememberChromeInk
import com.mobixournal.ui.theme.rememberGlassSurfaceColor
import com.mobixournal.ui.theme.rememberPopupPanelColor

internal enum class SheetTab { DOCUMENT, STUDIO }
private enum class SheetView { EXPORT, SHARE, SAVE_AS }

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

private data class SheetPopupLayout(
    val isCompact: Boolean,
    val contentWidthPx: Int,
    val positionProvider: PopupPositionProvider?,
    val motionRole: DesignTokens.GlassMotion.SurfaceRole,
)

@Composable
private fun rememberSheetPopupLayout(): SheetPopupLayout {
    val policy = LocalChromeLayoutPolicy.current
    val density = LocalDensity.current
    val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    return remember(density, screenWidthDp, policy) {
        val widthDp = if (policy.overflowSheetIsBottom) {
            (screenWidthDp.dp - 24.dp).coerceAtMost(policy.overflowSheetMaxWidthDp.dp)
        } else {
            policy.overflowSheetMaxWidthDp.dp
        }
        val widthPx = with(density) { widthDp.roundToPx() }
        val provider = if (policy.overflowSheetIsBottom) {
            BottomSheetPopupPositionProvider(with(density) { 12.dp.roundToPx() })
        } else {
            null
        }
        val role = if (policy.overflowSheetIsBottom) {
            DesignTokens.GlassMotion.SurfaceRole.SHEET
        } else {
            DesignTokens.GlassMotion.SurfaceRole.POPOVER
        }
        SheetPopupLayout(
            isCompact = policy.isCompact,
            contentWidthPx = widthPx,
            positionProvider = provider,
            motionRole = role,
        )
    }
}

@Composable
internal fun rememberSheetSurfaceColor(): Color {
    val isDark = chromeIsDark()
    return rememberChromeInk().copy(alpha = if (isDark) 0.08f else 0.055f)
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
    onShare: (ShareFormat) -> Unit = {},
    onSaveAsFormat: (SaveFormat) -> Unit = { ui.showSaveAs = true },
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
        onShare = onShare,
        onSaveAsFormat = onSaveAsFormat,
        onOpenSearch = { pane.searchOpen = true },
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
    onShare: (ShareFormat) -> Unit = {},
    onSaveAsFormat: (SaveFormat) -> Unit = {},
    onOpenSearch: () -> Unit = {},
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
    var subView by remember { mutableStateOf<SheetView?>(null) }

    val layout = rememberSheetPopupLayout()
    val isCompact = layout.isCompact
    val sheetFill = rememberPopupPanelColor()

    val closeSheet = {
        open = false
        subView = null
    }

    Box {
        OverflowTriggerButton(
            expanded = open,
            onClick = { subView = null; open = true },
        )
        ToolbarMenu(
            expanded = open,
            onDismissRequest = closeSheet,
            positionProvider = layout.positionProvider,
            contentWidthPx = layout.contentWidthPx,
            fill = sheetFill,
            motionRole = layout.motionRole,
            modifier = if (isCompact) Modifier.navigationBarsPadding() else Modifier,
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
                subView = subView,
                onSelectTab = { tab -> activeTab = tab; subView = null },
                onOpenSubView = { subView = it },
                onBackFromSubView = { subView = null },
                onDismiss = closeSheet,
                onNewTab = onNewTab,
                onOpen = onOpen,
                onSaveAsFormat = onSaveAsFormat,
                onOpenSearch = onOpenSearch,
                onImportPdf = onImportPdf,
                onExportPdf = onExportPdf,
                onExportPagePng = onExportPagePng,
                onExportPageSvg = onExportPageSvg,
                onSavePortableCopy = onSavePortableCopy,
                onShare = onShare,
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
    val tint = if (expanded) MaterialTheme.colorScheme.primary else rememberChromeInk()
    HeaderActionButton(
        onClick = onClick,
        container = if (expanded) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
    ) {
        Icon(
            ChromeGlyph.MENU.imageVector(),
            contentDescription = "Menu",
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
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
    subView: SheetView?,
    onSelectTab: (SheetTab) -> Unit,
    onOpenSubView: (SheetView) -> Unit,
    onBackFromSubView: () -> Unit,
    onDismiss: () -> Unit,
    onNewTab: () -> Unit,
    onOpen: () -> Unit,
    onSaveAsFormat: (SaveFormat) -> Unit,
    onOpenSearch: () -> Unit,
    onImportPdf: () -> Unit,
    onExportPdf: () -> Unit,
    onExportPagePng: () -> Unit,
    onExportPageSvg: () -> Unit,
    onSavePortableCopy: () -> Unit,
    onShare: (ShareFormat) -> Unit,
    onSelectPenPreset: (PenPreset) -> Unit,
    onToggleFingerDraws: () -> Unit,
    onToggleFullPage: () -> Unit,
    onToggleToolbarPosition: () -> Unit,
    onTogglePenDiagnostics: () -> Unit,
    onOpenPenParameters: () -> Unit,
    onSettings: () -> Unit,
) {
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
            onShareClick = { onOpenSubView(SheetView.SHARE) },
        )
        OverflowSheetContent(
            activeTab = activeTab,
            subView = subView,
            onSelectTab = onSelectTab,
            isReadOnly = isReadOnly,
            fullPage = fullPage,
            settings = settings,
            penDiagnostics = penDiagnostics,
            onOpenSubView = onOpenSubView,
            onBackFromSubView = onBackFromSubView,
            onDismiss = onDismiss,
            onNewTab = onNewTab,
            onOpen = onOpen,
            onSaveAsFormat = onSaveAsFormat,
            onOpenSearch = onOpenSearch,
            onImportPdf = onImportPdf,
            onExportPdf = onExportPdf,
            onExportPagePng = onExportPagePng,
            onExportPageSvg = onExportPageSvg,
            onSavePortableCopy = onSavePortableCopy,
            onShare = onShare,
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
    activeTab: SheetTab,
    subView: SheetView?,
    onSelectTab: (SheetTab) -> Unit,
    isReadOnly: Boolean,
    fullPage: Boolean,
    settings: AppSettings?,
    penDiagnostics: Boolean,
    onOpenSubView: (SheetView) -> Unit,
    onBackFromSubView: () -> Unit,
    onDismiss: () -> Unit,
    onNewTab: () -> Unit,
    onOpen: () -> Unit,
    onSaveAsFormat: (SaveFormat) -> Unit,
    onOpenSearch: () -> Unit,
    onImportPdf: () -> Unit,
    onExportPdf: () -> Unit,
    onExportPagePng: () -> Unit,
    onExportPageSvg: () -> Unit,
    onSavePortableCopy: () -> Unit,
    onShare: (ShareFormat) -> Unit,
    onSelectPenPreset: (PenPreset) -> Unit,
    onToggleFingerDraws: () -> Unit,
    onToggleFullPage: () -> Unit,
    onToggleToolbarPosition: () -> Unit,
    onTogglePenDiagnostics: () -> Unit,
) {
    // Keep the mode switcher inside the root branch so entering a drilldown (Share / Save As /
    // Export) runs a single AnimatedContent transition and SizeTransform without animateContentSize
    // fighting it or snapping the switcher out on frame zero.
    AnimatedContent(
        targetState = subView,
        transitionSpec = { liquidGlassFadeTransform() },
        label = "OverflowSheetView",
    ) { targetSubView ->
        when (targetSubView) {
            null -> OverflowSheetRootView(
                activeTab = activeTab,
                onSelectTab = onSelectTab,
                isReadOnly = isReadOnly,
                fullPage = fullPage,
                settings = settings,
                penDiagnostics = penDiagnostics,
                onOpenSubView = onOpenSubView,
                onDismiss = onDismiss,
                onNewTab = onNewTab,
                onOpen = onOpen,
                onOpenSearch = onOpenSearch,
                onImportPdf = onImportPdf,
                onSelectPenPreset = onSelectPenPreset,
                onToggleFingerDraws = onToggleFingerDraws,
                onToggleFullPage = onToggleFullPage,
                onToggleToolbarPosition = onToggleToolbarPosition,
                onTogglePenDiagnostics = onTogglePenDiagnostics,
            )
            SheetView.EXPORT -> ExportDrilldownView(
                isReadOnly = isReadOnly,
                onBack = onBackFromSubView,
                onExportPdf = { onDismiss(); onExportPdf() },
                onExportPng = { onDismiss(); onExportPagePng() },
                onExportSvg = { onDismiss(); onExportPageSvg() },
                onSavePortable = { onDismiss(); onSavePortableCopy() },
            )
            SheetView.SHARE -> ShareDrilldownView(
                isReadOnly = isReadOnly,
                onBack = onBackFromSubView,
                onShare = { format -> onDismiss(); onShare(format) },
            )
            SheetView.SAVE_AS -> SaveAsDrilldownView(
                isReadOnly = isReadOnly,
                onBack = onBackFromSubView,
                onPickFormat = { format -> onDismiss(); onSaveAsFormat(format) },
            )
        }
    }
}

@Composable
private fun OverflowSheetRootView(
    activeTab: SheetTab,
    onSelectTab: (SheetTab) -> Unit,
    isReadOnly: Boolean,
    fullPage: Boolean,
    settings: AppSettings?,
    penDiagnostics: Boolean,
    onOpenSubView: (SheetView) -> Unit,
    onDismiss: () -> Unit,
    onNewTab: () -> Unit,
    onOpen: () -> Unit,
    onOpenSearch: () -> Unit,
    onImportPdf: () -> Unit,
    onSelectPenPreset: (PenPreset) -> Unit,
    onToggleFingerDraws: () -> Unit,
    onToggleFullPage: () -> Unit,
    onToggleToolbarPosition: () -> Unit,
    onTogglePenDiagnostics: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SheetModeSwitcher(activeTab = activeTab, onSelectTab = onSelectTab)
        AnimatedContent(
            targetState = activeTab,
            transitionSpec = { liquidGlassFadeTransform() },
            label = "OverflowSheetTab",
        ) { tab ->
            when (tab) {
                SheetTab.DOCUMENT -> DocumentTabView(
                    isReadOnly = isReadOnly,
                    showSearchAction = LocalChromeLayoutPolicy.current.isCompact,
                    onNewTab = { onDismiss(); onNewTab() },
                    onOpen = { onDismiss(); onOpen() },
                    onSaveAs = { onOpenSubView(SheetView.SAVE_AS) },
                    onImportPdf = { onDismiss(); onImportPdf() },
                    onOpenExport = { onOpenSubView(SheetView.EXPORT) },
                    onOpenSearch = { onDismiss(); onOpenSearch() },
                )
                SheetTab.STUDIO -> StudioTabView(
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
            }
        }
    }
}

@Composable
private fun DocumentHeroCard(
    title: String,
    pageLabel: String,
    hasUnsavedChanges: Boolean,
    isReadOnly: Boolean,
    onShareClick: () -> Unit,
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
                .clickable(enabled = !isReadOnly, onClick = onShareClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Share,
                contentDescription = "Share",
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
