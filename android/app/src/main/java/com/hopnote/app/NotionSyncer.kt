package com.hopnote.app

class NotionSyncer(private val dao: CaptureDao, private val preferences: NotionPreferences) {
    suspend fun syncPending() {
        val connection = preferences.connection() ?: return
        val pageId = connection.hopNotePageId ?: return
        for (capture in dao.unsynced()) {
            dao.markSyncing(capture.id)
            NotionClient.appendCapture(connection.copy(hopNotePageId = pageId), capture)
                .onSuccess { blockId -> dao.markSynced(capture.id, System.currentTimeMillis(), blockId) }
                .onFailure { dao.markFailed(capture.id) }
        }
    }
}
