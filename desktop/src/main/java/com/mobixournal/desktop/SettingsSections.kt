package com.mobixournal.desktop

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The **sections** of the window's Settings: the page of controls for one topic.
 *
 * The phone's settings are four areas over twelve sections (`SettingsArea` / `SettingsSection` in
 * `app/src/main/java/com/mobixournal/ui/SettingsScreen.kt`); the window starts with the three that have
 * controls to offer here — where the tool strips stand, what the status bar reads, and which pen stream the
 * window opens. The rest of the phone's list is not copied: a section the window cannot answer is a page of
 * disabled rows, which is worse than a shorter list, and each one arrives with the task that gives it a
 * setting to change (`TODO.toml`).
 *
 * @property title The section's name, as the index on the left shows it.
 * @property summary One line saying what the section covers, under the title in the index.
 * @property keywords Search aliases — the words a reader types when they do not know what the window calls
 *   the thing, so the field **finds** a section rather than filtering titles.
 */
enum class DesktopSettingSection(val title: String, val summary: String, val keywords: String) {
    /** Where the strips stand, and how a document opens. */
    INTERFACE(
        "Interface",
        "Where the tool strips stand, and how a document opens.",
        "toolbar tools strip rail ribbon top side left right placement layout fit zoom sidebar page list " +
            "inspector columns windows",
    ),

    /** What the status bar reports. */
    DISPLAY(
        "Display",
        "The status bar's readouts.",
        "display status bar readout frame time paint milliseconds theme dark light refresh rate",
    ),

    /** Which pen stream the window reads. */
    TABLET(
        "Tablet",
        "The pen stream the window reads.",
        "tablet pen stylus wintab windows ink pressure stream driver",
    ),
    ;

    /** Whether this section answers [needle] — its title, its summary or one of its aliases. */
    fun matches(needle: String): Boolean {
        if (needle.isBlank()) return true
        val lower = needle.trim().lowercase()
        return title.lowercase().contains(lower) ||
            summary.lowercase().contains(lower) ||
            keywords.contains(lower)
    }

    companion object {
        /** The sections the search field leaves, in the index's own order. */
        fun search(query: String): List<DesktopSettingSection> = entries.filter { it.matches(query) }
    }
}

/**
 * The page of one section: its title and summary, then its cards.
 *
 * A null [section] is the search's own answer — the field left nothing to show — and it is a sentence
 * rather than a blank page, so the reader knows the word was the problem and not the window.
 */
@Composable
internal fun DesktopSettingPage(policy: DesktopSettingsState, section: DesktopSettingSection?) {
    if (section == null) {
        Text("No setting matches that.", color = DesktopTheme.inkSecondary, style = DesktopTheme.body)
        return
    }
    Text(section.title, color = DesktopTheme.ink, style = DesktopTheme.title)
    Spacer(Modifier.height(2.dp))
    Text(section.summary, color = DesktopTheme.inkSecondary, style = DesktopTheme.body)
    when (section) {
        DesktopSettingSection.INTERFACE -> InterfaceSettings(policy)
        DesktopSettingSection.DISPLAY -> DisplaySettings(policy)
        DesktopSettingSection.TABLET -> TabletSettings(policy)
    }
}

/**
 * **Interface**: the window's layout choices.
 *
 * The strips are the tablet's own answer plus the ribbon a window can afford: the phone's Main Toolbar is
 * a rail on the edge opposite the writing hand, and its figures sit in the top bar, so `Side`, `Top` and
 * `Both` are that shape with the ribbon made explicit (`StripPlacement`).
 */
@Composable
private fun InterfaceSettings(policy: DesktopSettingsState) {
    SectionCard("Tool strips") {
        SettingsChoice(
            label = "Placement",
            subtitle = "Side is the tablet's rail; Top puts the same tools under the header; Both keeps each lane.",
            options = StripPlacement.entries,
            picked = policy.stripPlacement,
            optionLabel = { it.label },
            onPick = policy::chooseStripPlacement,
        )
        if (policy.stripPlacement.showsRail) {
            SettingsChoice(
                label = "Side",
                subtitle = "The edge opposite your writing hand: Left for a right hand.",
                options = RailSide.entries,
                picked = policy.railSide,
                optionLabel = { it.label },
                onPick = policy::chooseRailSide,
            )
        }
    }
    SectionCard("Opening a document") {
        SettingsChoice(
            label = "Zoom",
            subtitle = "Automatic fits a tall page to the width and anything else to the page.",
            options = FitOnOpen.entries,
            picked = policy.fitOnOpen,
            optionLabel = { it.label },
            onPick = policy::chooseFitOnOpen,
        )
        SettingsNote("The next document you open; the one on screen keeps its zoom.")
    }
    SectionCard("Windows") {
        SettingsChoice(
            label = "Page list",
            subtitle = "Automatic shows it once a document has more than one page.",
            options = SidebarPolicy.entries,
            picked = policy.sidebarPolicy,
            optionLabel = { it.label },
            onPick = policy::chooseSidebarPolicy,
        )
        SettingsChoice(
            label = "Inspector",
            subtitle = "The column that edits the element in your hand. Never keeps it shut.",
            options = InspectorPolicy.entries,
            picked = policy.inspectorPolicy,
            optionLabel = { it.label },
            onPick = policy::chooseInspectorPolicy,
        )
    }
}

/** **Display**: what the status bar reports, and what the window can say about the panel it draws on. */
@Composable
private fun DisplaySettings(policy: DesktopSettingsState) {
    SectionCard("Status bar") {
        SwitchRow("Frame time", policy.frameTime) { policy.chooseFrameTime(it) }
        Readout("Display", refreshLabel(displayRefreshHz()))
        frameLabel(FrameMeter.averageMs, policy.frameTime)?.let { Readout("Painting", it) }
    }
    SettingsNote("The window is dark only in this build.")
}

/** **Tablet**: the one choice the pen readers offer — which stream the window asks first. */
@Composable
private fun TabletSettings(policy: DesktopSettingsState) {
    SectionCard("Pen") {
        SettingsChoice(
            label = "Stream",
            subtitle = "Wintab carries the pressure at the tablet's own rate; Windows Ink is the pointer stream.",
            options = PenStream.entries,
            picked = policy.penStream,
            optionLabel = { it.label },
            onPick = policy::choosePenStream,
        )
    }
    SettingsNote("The status bar names the stream actually open, so a preference this host cannot honour says so.")
}
