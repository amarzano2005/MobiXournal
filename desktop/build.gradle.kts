/**
 * The desktop shell — the first, deliberately small slice of Step 3 (`docs/desktop-workstation.md`).
 *
 * It is a **spike**: it reuses the app's platform-free `format` package (`.xopp` / `.mobix` model and
 * codecs) by compiling that one source directory, and paints pages with Compose Multiplatform on
 * Skiko. It does not yet share the editor: the Android `render`, `ui`, `tabs` and `io` packages are
 * Android-bound and arrive with the Phase 3 module extraction (`mobix-model`, `mobix-format`,
 * `mobix-render`, `mobix-ui-common`). Keeping the format sources in one place — instead of copying
 * them — is what makes this spike a proof of the real architecture rather than a fork of it.
 */
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(21)
}

sourceSets.getByName("main").kotlin.run {
    srcDir("../app/src/main/java")
    filter.include(
        "com/mobixournal/format/**",
        // BackgroundGrid is documented as platform-free geometry, so the sheet rules identically.
        "com/mobixournal/render/BackgroundGrid.kt",
        // This module's own sources, matched against its own root by the same filter.
        "com/mobixournal/desktop/**",
    )
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    testImplementation(libs.junit)
}

// `run` starts in the module directory by default; run from the repository root so a path argument
// like `--args="udiff.xopp"` means the file the reader is looking at.
tasks.withType<JavaExec>().configureEach {
    workingDir = rootProject.projectDir
}

compose.desktop {
    application {
        mainClass = "com.mobixournal.desktop.MainKt"

        nativeDistributions {
            packageName = "MobiXournal"
            packageVersion = "1.4.0"
            description = "MobiXournal desktop preview"
        }
    }
}
