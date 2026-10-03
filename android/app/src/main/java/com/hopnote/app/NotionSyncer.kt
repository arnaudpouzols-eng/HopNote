package com.hopnote.app

class NotionSyncer(private val dao: CaptureDao, private val session: HopNoteSession) {
    /** Returns false when Android should retry the persistent sync queue later. */
    suspend fun syncPending(): Boolean {
        // After a network error or an interrupted app process, notes remain queued rather
        // than looking permanently failed or in progress.
        dao.resetPendingForRetry()
        if (dao.unsynced().isEmpty()) return true
        // There is nothing to send until Notion has been connected by the person using HopNote.
        if (session.token() == null) return true
        if (!HopNoteApi.connected(session)) return false

        var allSent = true
        for (capture in dao.unsynced()) {
            dao.markSyncing(capture.id)
            HopNoteApi.append(session, capture)
                .onSuccess { blockId -> dao.markSynced(capture.id, System.currentTimeMillis(), blockId) }
                .onFailure { dao.markPending(capture.id); allSent = false }
        }
        return allSent
    }
}
