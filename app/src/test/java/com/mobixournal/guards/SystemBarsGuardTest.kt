package com.mobixournal.guards

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mechanical guard enforcing the transparent system bars contract documented in `docs/chrome.md`
 * and tracked as `guard-the-system-bars-as-one-transparent-contract` in `TODO.toml`.
 *
 * The contract:
 *  "The app owns no colour of its own in the system bars, only icons."
 *
 * Any Kotlin source or XML theme resource in `app/src/main` that assigns `statusBarColor` or
 * `navigationBarColor` must assign a transparent color (`@android:color/transparent`,
 * `Color.Transparent.toArgb()`, or `android.graphics.Color.TRANSPARENT`).
 *
 * An opaque assignment (e.g. `colorScheme.background`) repaints the system bar over the full-bleed
 * canvas window, hiding page content (such as the bottom 64px under the navigation bar).
 */
class SystemBarsGuardTest {

    private val transparentKotlinExpressions = listOf(
        "Color.Transparent.toArgb()",
        "android.graphics.Color.TRANSPARENT",
        "Color.TRANSPARENT",
        "0",
    )

    private val transparentXmlValues = listOf(
        "@android:color/transparent",
        "@color/transparent",
        "#00000000",
        "@null",
    )

    private val systemBarAssignmentRegex = Regex(
        """(?:window\.)?(statusBarColor|navigationBarColor)\s*=\s*([^\r\n;]+)"""
    )

    private val xmlSystemBarRegex = Regex(
        """<item\s+name="android:(statusBarColor|navigationBarColor)">([^<]+)</item>"""
    )

    @Test
    fun allKotlinAssignmentsToSystemBarColorsAreTransparent() {
        val violations = mutableListOf<String>()
        val sources = mainKotlinSources()
        assertTrue("main Kotlin source set must not be empty", sources.isNotEmpty())

        var foundAssignments = 0
        for (file in sources) {
            val text = file.readText()
            val cleanCode = stripCommentsAndStrings(text)
            for (match in systemBarAssignmentRegex.findAll(cleanCode)) {
                foundAssignments++
                val property = match.groupValues[1]
                val rhs = match.groupValues[2].trim()
                if (!isTransparentKotlinExpression(rhs)) {
                    val line = lineOf(text, match.range.first)
                    violations += "${file.name}:$line assigns non-transparent $property = $rhs"
                }
            }
        }

        assertTrue(
            "Found no system bar assignments in Kotlin sources. The scanner may not be reading sources.",
            foundAssignments >= 2,
        )
        assertTrue(
            "System bar colors must only be assigned transparent values:\n  " + violations.joinToString("\n  "),
            violations.isEmpty(),
        )
    }

    @Test
    fun allXmlThemeSystemBarColorsAreTransparent() {
        val violations = mutableListOf<String>()
        val xmlFiles = mainXmlResources()
        assertTrue("main XML resource set must not be empty", xmlFiles.isNotEmpty())

        var foundThemeEntries = 0
        for (file in xmlFiles) {
            val text = file.readText()
            for (match in xmlSystemBarRegex.findAll(text)) {
                foundThemeEntries++
                val attr = match.groupValues[1]
                val value = match.groupValues[2].trim()
                if (value !in transparentXmlValues) {
                    val line = lineOf(text, match.range.first)
                    violations += "${file.name}:$line sets android:$attr to non-transparent $value"
                }
            }
        }

        assertTrue(
            "Found no system bar attributes in XML themes. The scanner may not be reading resources.",
            foundThemeEntries >= 4,
        )
        assertTrue(
            "XML theme system bars must only be transparent:\n  " + violations.joinToString("\n  "),
            violations.isEmpty(),
        )
    }

    @Test
    fun guardRejectsOpaqueKotlinAssignments() {
        assertFalse(isTransparentKotlinExpression("colorScheme.background.toArgb()"))
        assertFalse(isTransparentKotlinExpression("Color.Black.toArgb()"))
        assertFalse(isTransparentKotlinExpression("0xFF000000.toInt()"))
        assertFalse(isTransparentKotlinExpression("MaterialTheme.colorScheme.surface"))

        assertTrue(isTransparentKotlinExpression("Color.Transparent.toArgb()"))
        assertTrue(isTransparentKotlinExpression("android.graphics.Color.TRANSPARENT"))
    }

    private fun isTransparentKotlinExpression(expr: String): Boolean {
        val trimmed = expr.trim()
        return transparentKotlinExpressions.any {
            trimmed == it || (it.startsWith("Color.Transparent") && trimmed.startsWith(it))
        }
    }

    private fun lineOf(text: String, index: Int): Int =
        text.take(index).count { it == '\n' } + 1

    private fun stripCommentsAndStrings(source: String): String {
        val out = StringBuilder(source.length)
        var i = 0
        var state = 0 // 0 code, 1 line comment, 2 block comment, 3 string, 4 raw string
        while (i < source.length) {
            val c = source[i]
            val next = source.getOrNull(i + 1)
            when (state) {
                0 -> when {
                    c == '/' && next == '/' -> { state = 1; out.append("  "); i++ }
                    c == '/' && next == '*' -> { state = 2; out.append("  "); i++ }
                    c == '"' && next == '"' && source.getOrNull(i + 2) == '"' -> {
                        state = 4; out.append("   "); i += 2
                    }
                    c == '"' -> { state = 3; out.append(' ') }
                    else -> out.append(c)
                }
                1 -> { if (c == '\n') { state = 0; out.append('\n') } else out.append(' ') }
                2 -> if (c == '*' && next == '/') { state = 0; out.append("  "); i++ } else {
                    out.append(if (c == '\n') '\n' else ' ')
                }
                3 -> if (c == '\\') { out.append("  "); i++ } else if (c == '"' || c == '\n') {
                    state = 0; out.append(' ')
                } else out.append(' ')
                4 -> if (c == '"' && next == '"' && source.getOrNull(i + 2) == '"') {
                    state = 0; out.append("   "); i += 2
                } else out.append(if (c == '\n') '\n' else ' ')
            }
            i++
        }
        return out.toString()
    }

    private fun mainKotlinSources(): List<File> {
        val root = listOf(File("src/main/java/com/mobixournal"), File("app/src/main/java/com/mobixournal"))
            .firstOrNull { it.isDirectory }
            ?: error("no main Kotlin source set found")
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    private fun mainXmlResources(): List<File> {
        val root = listOf(File("src/main/res"), File("app/src/main/res"))
            .firstOrNull { it.isDirectory }
            ?: error("no main XML res directory found")
        return root.walkTopDown().filter { it.isFile && it.extension == "xml" }.toList()
    }
}
