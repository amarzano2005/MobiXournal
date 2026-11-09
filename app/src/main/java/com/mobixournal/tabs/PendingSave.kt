package com.mobixournal.tabs

import com.mobixournal.format.SaveFormat

/** A picker answers for this tab and format, regardless of later focus changes. */
data class PendingSave(val paneIndex: Int, val tabId: String, val format: SaveFormat, val name: String) {
    fun tabIn(session: TabSessions): OpenTab? = session.tabs.firstOrNull { it.id == tabId }
}

/** A cancelled picker discards its proposal, never the document's committed format or URI. */
data class SavePickerState(val request: PendingSave? = null, val uri: String? = null) {
    fun answered(uri: String?): SavePickerState = if (uri == null) SavePickerState() else copy(uri = uri)
}
