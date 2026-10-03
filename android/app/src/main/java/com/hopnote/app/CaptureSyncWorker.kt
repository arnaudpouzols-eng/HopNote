package com.hopnote.app

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.room.Room
import java.util.concurrent.TimeUnit

/**
 * Android keeps this task across app restarts. It runs only once a network is available,
 * and retries with a growing delay when Notion or the HopNote service cannot be reached.
 */
class CaptureSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val database = Room.databaseBuilder(applicationContext, HopNoteDatabase::class.java, "hopnote.db")
            .addMigrations(MainActivity.MIGRATION_1_2)
            .build()
        return try {
            val syncer = NotionSyncer(database.captures(), HopNoteSession(applicationContext))
            if (syncer.syncPending()) Result.success() else Result.retry()
        } finally {
            database.close()
        }
    }
}

object CaptureSyncQueue {
    private const val UNIQUE_WORK_NAME = "hopnote-capture-sync"

    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<CaptureSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        // A new capture, a return to the app, or a newly available connection should retry
        // immediately instead of waiting behind an older failed attempt's backoff timer.
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}
