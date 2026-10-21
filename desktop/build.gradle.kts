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
