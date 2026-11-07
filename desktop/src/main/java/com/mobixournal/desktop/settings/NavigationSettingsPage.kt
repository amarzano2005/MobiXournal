package com.mobixournal.desktop.settings

import androidx.compose.runtime.Composable
import java.util.Locale

/** **Navigation**: document open fit, page sidebar, zoom stepping and smooth scrolling. */
@Composable
internal fun NavigationSettings(policy: DesktopSettingsState) {
    val nav = policy.navigation
    SettingsGroup("Opening a document") {
        SettingsSelect(
            label = "Zoom",
            note = "Initial zoom fit. Automatic fits width for portrait pages, entire page otherwise.",
            options = FitOnOpen.entries,
            picked = policy.fitOnOpen,
            optionLabel = { it.label },
            onPick = policy::chooseFitOnOpen,
        )
    }
    SettingsGroup("Page list") {
        SettingsSelect(
            label = "Sidebar visibility",
            note = "Automatic displays sidebar when the document has multiple pages.",
            options = SidebarPolicy.entries,
            picked = policy.sidebarPolicy,
            optionLabel = { it.label },
            onPick = policy::chooseSidebarPolicy,
        )
    }
    SettingsGroup("Zoom step") {
        SettingsRow(
            label = "Wheel & dial rate",
            note = "Rate per mouse wheel notch (Ctrl + wheel) or dial notch. Default: 12%.",
        ) {
            SettingsSlider(
                value = nav.zoomStepWheel,
                range = DesktopNavigationStore.ZOOM_WHEEL_MIN..DesktopNavigationStore.ZOOM_WHEEL_MAX,
                step = DesktopNavigationStore.ZOOM_WHEEL_STEP,
                format = { String.format(Locale.ROOT, "%d %%", (it * 100).toInt()) },
                onValue = nav::chooseZoomStepWheel,
            )
        }
        SettingsRow(
            label = "Keyboard shortcut step",
            note = "Step size for Ctrl + and Ctrl - shortcuts. Default: 25%.",
        ) {
            SettingsSlider(
                value = nav.zoomStepKeyboard,
                range = DesktopNavigationStore.ZOOM_KEYBOARD_MIN..DesktopNavigationStore.ZOOM_KEYBOARD_MAX,
                step = DesktopNavigationStore.ZOOM_KEYBOARD_STEP,
                format = { String.format(Locale.ROOT, "+%d %%", ((it - 1f) * 100).toInt()) },
                onValue = nav::chooseZoomStepKeyboard,
            )
        }
    }
    SettingsGroup("Scrolling") {
        SettingsRow(
            label = "Smooth scrolling",
            note = "Glide smoothly between wheel notches and after a hand pan, instead of stopping dead.",
            onClick = { nav.chooseSmoothScroll(!nav.smoothScroll) },
        ) {
            SettingsSwitch(nav.smoothScroll)
        }
        SettingsRow(
            label = "Scroll distance",
            note = "Distance scrolled per mouse notch, per dp of a hand pan, and per dp of the pen. Default: 64 dp.",
        ) {
            SettingsSlider(
                value = nav.scrollStepDp,
                range = DesktopNavigationStore.SCROLL_STEP_MIN..DesktopNavigationStore.SCROLL_STEP_MAX,
                step = DesktopNavigationStore.SCROLL_STEP_STEP,
                format = { String.format(Locale.ROOT, "%d dp", it.toInt()) },
                onValue = nav::chooseScrollStepDp,
            )
        }
        if (nav.smoothScroll) {
            SettingsRow(
                label = "Scroll fluidity",
                note = "Deceleration response of the glide after a notch or a pan: crisp at lower values, soft glide at higher.",
            ) {
                SettingsSlider(
                    value = nav.scrollFluidity,
                    range = DesktopNavigationStore.FLUIDITY_MIN..DesktopNavigationStore.FLUIDITY_MAX,
                    step = DesktopNavigationStore.FLUIDITY_STEP,
                    format = { String.format(Locale.ROOT, "%d %%", (it * 100).toInt()) },
                    onValue = nav::chooseScrollFluidity,
                )
            }
        }
        SettingsCommand(
            label = "Reset to defaults",
            note = "Restore zoom steps and scrolling parameters to factory defaults.",
            onClick = nav::resetToDefaults,
        )
    }
}
