package com.mobixournal.ui
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PageSizeSection(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    var widthText by remember { mutableStateOf(String.format(java.util.Locale.US, "%.1f", settings.defaultPageWidthPt)) }
    var heightText by remember { mutableStateOf(String.format(java.util.Locale.US, "%.1f", settings.defaultPageHeightPt)) }

    Text("Default page size for new documents", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(8.dp))

    OutlinedTextField(
        value = widthText,
        onValueChange = {
            widthText = it
            it.replace(',', '.').toDoubleOrNull()?.let { w ->
                onChange(settings.copy(defaultPageWidthPt = w.coerceIn(72.0, 14400.0)))
            }
        },
        label = { Text("Width (pt)") },
        supportingText = { Text("A4 = 595.3 pt, Letter = 612 pt") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )

    Spacer(Modifier.height(8.dp))

    OutlinedTextField(
        value = heightText,
        onValueChange = {
            heightText = it
            it.replace(',', '.').toDoubleOrNull()?.let { h ->
                onChange(settings.copy(defaultPageHeightPt = h.coerceIn(72.0, 14400.0)))
            }
        },
        label = { Text("Height (pt)") },
        supportingText = { Text("A4 = 841.9 pt, Letter = 792 pt") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}