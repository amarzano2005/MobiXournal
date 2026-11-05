package com.mobixournal.ui

/**
 * Every preference at its factory value, **except** the ones that record that something has already
 * been shown.
 *
 * Resetting the preferences is not the same act as installing the app: the welcome tour was answered,
 * and a reset that brought it back would make *back to factory values* also mean *and sit through the
 * introduction again* — an introduction is not a setting. [AppSettings.hasSeenOnboarding] is the one
 * flag of this kind today; a second one is carried over here rather than at a call site, so no screen
 * has to remember the rule.
 *
 * It lives beside [AppSettings] rather than inside it because it is the one place that *reads* the
 * settings object to build another one, and `SourceStructureTest` holds that file to its ceiling: a rule
 * with a test of its own earns a file of its own.
 */
fun AppSettings.factoryDefaults(): AppSettings = AppSettings(hasSeenOnboarding = hasSeenOnboarding)
