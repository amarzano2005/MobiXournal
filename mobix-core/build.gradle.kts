/**
 * The platform-free core: the document model, the `.xopp` and `.mobix` codecs and the geometry both
 * shells paint with (Phase A of `step-3-desktop-workstation` in `TODO.toml`).
 *
 * A plain JVM library on purpose. The Android app and the desktop shell both depend on it, and
 * nothing in here may import `android.*` or Compose — that restriction is what keeps the desktop
 * preview a second *shell* over one implementation instead of a fork. The Android app consuming a
 * JVM library is fine: it is the direction that does not need a KMP target yet, and the module can
 * grow android/jvm targets later without moving any of this code.
 */
plugins {
    alias(libs.plugins.kotlin.jvm)
    // `SyntheticNotebook` is a test fixture, not a test: the app's I/O and render suites build a real
    // `.mobix` with it, and so do this module's own ones. Compiling it once here is what stops the
    // two suites from growing their own copies of the same archive.
    `java-test-fixtures`
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // The Android app compiles to Java 17; a library it links must not be newer than that.
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

sourceSets.getByName("test") {
    // The `.xopp` fixtures are shared with the app's integration tests (`io/DeviceFixturesTest`):
    // they stay in one place under `app/src/test/resources` and are read from here, not copied.
    resources.srcDir("../app/src/test/resources")
}

dependencies {
    testImplementation(libs.junit)
}
