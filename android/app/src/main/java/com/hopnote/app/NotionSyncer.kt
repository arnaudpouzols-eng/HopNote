package com.hopnote.app

class NotionSyncer(private val dao: CaptureDao, private val session: HopNoteSession) {
    suspend fun syncPending() {
        if (!HopNoteApi.connected(session)) return
        for (capture in dao.unsynced()) {
            dao.markSyncing(capture.id)
            HopNoteApi.append(session, capture)
                .onSuccess { blockId -> dao.markSynced(capture.id, System.currentTimeMillis(), blockId) }
                .onFailure { dao.markFailed(capture.id) }
        }
    }
}
