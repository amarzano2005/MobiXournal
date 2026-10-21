package com.mobixournal.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import java.awt.FileDialog
import java.io.File

/**
 * The desktop shell: open a document, look at its pages, zoom.
 *
 * That is the whole feature set on purpose. It is the thinnest thing that proves the claim in
 * `docs/desktop-workstation.md` — the document model and the `.xopp` codecs are platform-free, so a
 * desktop window can show the **same** page the Android editor shows — before any of the editor's
 * Android-bound UI is extracted into shared modules.
 */
@Composable
fun FrameWindowScope.DesktopApp(initial: File? = null) {
    var document by remember { mutableStateOf<DesktopDocument?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var zoom by remember { mutableStateOf(1f) }

    fun open(file: File) {
        runCatching { DocumentLoader.load(file) }
            .onSuccess {
                document = it
                error = null
                zoom = 1f
            }
            .onFailure { error = it.message ?: it.toString() }
    }

    fun pick(): File? {
        val dialog = FileDialog(window, "Apri un documento", FileDialog.LOAD)
        dialog.setFilenameFilter { _, name -> name.endsWith(".xopp", ignoreCase = true) }
        dialog.isVisible = true
        val dir = dialog.directory
        val name = dialog.file
        return if (dir != null && name != null) File(dir, name) else null
    }

    LaunchedEffect(initial) { initial?.let(::open) }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Column(Modifier.fillMaxSize().background(Color(0xFF17171B))) {
            Toolbar(
                document = document,
                zoom = zoom,
                onZoom = { zoom = it },
                onOpen = { pick()?.let(::open) },
            )
            Box(Modifier.fillMaxSize()) {
                val doc = document
                when {
                    error != null -> Notice("Impossibile aprire il documento", error!!)
                    doc == null -> Notice(
                        "Apri un file .xopp per iniziare",
                        "Anteprima desktop: pagine e inchiostro in sola lettura. File → Apri, oppure passa un percorso come argomento.",
                    )
                    else -> PageList(doc, zoom)
                }
            }
        }
    }
}

@Composable
private fun Toolbar(
    document: DesktopDocument?,
    zoom: Float,
    onZoom: (Float) -> Unit,
    onOpen: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).background(Color(0xFF232329)).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = onOpen) { Text("Apri…") }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = document?.file?.name ?: "Nessun documento aperto",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = document?.let { "${it.pageCount} pagine · ${it.strokeCount} tratti" } ?: "anteprima desktop",
                color = Color(0xFF9A9AA6),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Spacer(Modifier.weight(1f))
        Text("Zoom ${(zoom * 100).toInt()}%", color = Color(0xFF9A9AA6))
        Spacer(Modifier.width(12.dp))
        Slider(
            value = zoom,
            onValueChange = onZoom,
            valueRange = 0.25f..2f,
            modifier = Modifier.width(180.dp),
        )
    }
}

@Composable
private fun PageList(document: DesktopDocument, zoom: Float) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        document.document.pages.forEachIndexed { index, page ->
            Text(
                text = "Pagina ${index + 1} / ${document.pageCount}",
                color = Color(0xFF9A9AA6),
                style = MaterialTheme.typography.labelSmall,
            )
            PageView(
                page = page,
                scale = zoom,
                modifier = Modifier.padding(bottom = 18.dp).shadow(10.dp),
            )
        }
    }
}

@Composable
private fun Notice(title: String, detail: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(detail, color = Color(0xFF9A9AA6), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
