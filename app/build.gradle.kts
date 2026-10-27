plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/** Test-only handle on the raw pdfbox-android AAR, so its bundled assets can be extracted below. */
val pdfboxAar: Configuration by configurations.creating { isTransitive = false }

android {
    namespace = "com.mobixournal"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mobixournal"
        minSdk = 26
        targetSdk = 34
        // The tablet app's own line, restarted at 2.0.0 when the v2 channel became the product.
        // The Windows window numbers itself separately (desktop/build.gradle.kts, from 1.0.0): no
        // version is derived from the other, and `scripts/todo.sh versions` reads both.
        versionCode = 6
        versionName = "2.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    /**
     * The debug build is the **v2 development channel**, and it deliberately installs as its own app:
     * `applicationIdSuffix` gives it the package `com.mobixournal.v2` (and the `debug` source set its
     * own `app_name`, "MobiXournal v2 (dev)"), so `scripts/build.sh` — and therefore the APK the
     * release workflow attaches — never replaces an installed `com.mobixournal`.
     *
     * That matters because the Android package *is* the app's identity: installing a build of the same
     * package with a different signing key (the release APK carried the debug key; a rebuild can carry
     * another) replaces the user's install, taking the session cache and settings with it. The owner
     * runs the released app daily while this channel is built, so the two have to coexist: same
     * launcher, two icons.
     *
     * **Ending the transition** is this one line: when the v2 line is ready to *take over* the daily
     * app, delete the `debug` block (and `src/debug/res/`) so the build is `com.mobixournal` again and
     * upgrades the existing install in place. Kept as a deliberate line in the diff, like the
     * mimetype deviation in `format/XoppZip.kt`, because the alternative is a silent rename nobody sees.
     * See "The v2 development channel" in `docs/tools.md`.
     */
    buildTypes {
        debug {
            applicationIdSuffix = ".v2"
            versionNameSuffix = "-v2"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            // PDFBox's font parser logs through android.util.Log; without this, loading a TTF in a
            // JVM unit test throws "not mocked" instead of returning a no-op.
            isReturnDefaultValues = true
        }
    }
    sourceSets.getByName("test") {
        resources.srcDir(layout.buildDirectory.dir("generated/pdfboxTestResources"))
    }
}

dependencies {
    // The document model, codecs and ink/paper geometry shared with the desktop shell.
    implementation(project(":mobix-core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.pdfbox.android)
    implementation(libs.mlkit.digital.ink)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    // `SyntheticNotebook`, the real `.mobix` fixture the I/O and render suites open.
    testImplementation(testFixtures(project(":mobix-core")))

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.espresso.core)

    pdfboxAar(libs.pdfbox.android)
}

// PDFBox ships its CMap/glyph-list data in the AAR's `assets/`, which it reaches through Android's
// AssetManager at runtime. JVM unit tests have no AssetManager, so PDFBox falls back to loading the
// same paths off the classpath — extract the AAR assets into the unit-test resources so font
// embedding (PDType0Font, "Identity-H") works in `testDebugUnitTest` the way it does on device.
val extractPdfboxAssets by tasks.registering(Copy::class) {
    from(provider { zipTree(pdfboxAar.singleFile) }) {
        include("assets/**")
        eachFile { path = path.removePrefix("assets/") }
    }
    into(layout.buildDirectory.dir("generated/pdfboxTestResources"))
    includeEmptyDirs = false
}

androidComponents.onVariants { variant ->
    tasks.matching { it.name == "process${variant.name.replaceFirstChar(Char::uppercase)}UnitTestJavaRes" }
        .configureEach { dependsOn(extractPdfboxAssets) }
}

// The documentation guards (`DocsGuardTest`, `SourceStructureTest`) read `docs/` and the source tree
// off disk at run time, and Gradle cannot see that: without declaring them, editing a doc leaves the
// test task UP-TO-DATE, so the guard that exists to catch a stale doc never runs against the doc that
// just changed. Measured: `touch docs/codebase.md && scripts/host-build.sh testDebugUnitTest` reported
// UP-TO-DATE in 4 s. Declaring the docs as inputs makes a doc edit re-run the guards here instead of in
// whatever clean build happens to come next. (`src/main/java` and `mobix-core` need no entry: a source
// change there alters the compiled classes, which the test task already tracks. `desktop/` is read by
// the same guard but is not a dependency of this module, so its own edit needs the entry. `AGENTS.md`
// and `README.md` are read by the same guards (`DocClaimsTest`, and the code-file check), so they are
// entries of their own: a front-door edit that skipped them would leave the guards unrun too.)
tasks.withType<Test>().configureEach {
    inputs.dir(rootProject.file("docs"))
        .withPropertyName("documentation")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.file("desktop/src/main/java"))
        .withPropertyName("desktopSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(rootProject.file("AGENTS.md"), rootProject.file("README.md"))
        .withPropertyName("frontDoorDocs")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
