/**
 * The desktop shell — the first, deliberately small slice of Step 3 (`docs/desktop-workstation.md`).
 *
 * It consumes `mobix-core` (the document model, the `.xopp` / `.mobix` codecs) and paints pages with
 * Compose Multiplatform on Skiko, so it is a second **shell** over one implementation rather than a
 * fork. It does not yet share the editor: the Android `render`, `ui`, `tabs` and `io` packages are
 * Android-bound, and each shared piece moves into `mobix-core` as its own task
 * (`step-3-desktop-workstation` in `TODO.toml`).
 */
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(21)
}


dependencies {
    implementation(project(":mobix-core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    // Rasterising a page's PDF background needs the JVM PDFBox; the Android port cannot run here.
    implementation(libs.pdfbox)
    // The pen's pressure, tilt and the dial's ring are not in AWT's pointer stream; Wintab is.
    implementation(libs.jna)
    testImplementation(libs.junit)
    // The `.mobix` fixtures are shared through `mobix-core`'s test fixtures, so this shell's suite
    // opens the same synthetic notebook the core and app suites do instead of growing its own writer.
    testImplementation(testFixtures(project(":mobix-core")))
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
            // The same art as the tablet's launcher: without it the packaged .exe is stamped with the
            // JVM's own icon, so an app that draws a notebook wore a coffee cup in Explorer.
            windows {
                iconFile.set(project.file("icon/mobixournal.ico"))
            }
        }
    }
}
