package com.drinkwithme.app.di

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.drinkwithme.app.DrinkWithMeApplication
import com.drinkwithme.app.polling.PollingScheduler
import com.drinkwithme.app.publish.RetryOutcome
import com.drinkwithme.app.publish.ShareRetryScheduler
import com.drinkwithme.app.push.SyncOutcome
import com.drinkwithme.app.supabase.SocialException
import java.util.concurrent.TimeUnit

/** Plant einen einmaligen Abgleich des Push-Abos mit dem Server. */
fun interface PushSyncScheduler {
    fun scheduleSync()
}

/** WorkManager-Aufträge der App: Abruf alle 15 Minuten, Push-Abo hochladen, Teilen nachholen. Alle nur mit Netz. */
class WorkManagerScheduler(private val context: Context) : PollingScheduler, ShareRetryScheduler, PushSyncScheduler {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override fun schedule() {
        val request = PeriodicWorkRequestBuilder<FeedPollingWorker>(15, TimeUnit.MINUTES)
            .setConstraints(network)
            .build()
        workManager.enqueueUniquePeriodicWork(POLLING, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun cancel() {
        workManager.cancelUniqueWork(POLLING)
    }

    /** Einmaliger Abruf sofort (z. B. beim Öffnen der App), zusätzlich zum regelmäßigen. */
    fun pollNow() {
        val request = OneTimeWorkRequestBuilder<FeedPollingWorker>().setConstraints(network).build()
        workManager.enqueueUniqueWork(POLL_NOW, ExistingWorkPolicy.KEEP, request)
    }

    override fun scheduleRetry() = enqueueOnce<ShareRetryWorker>(SHARE_RETRY)

    override fun scheduleSync() = enqueueOnce<PushSyncWorker>(PUSH_SYNC)

    private inline fun <reified W : CoroutineWorker> enqueueOnce(name: String) {
        val request = OneTimeWorkRequestBuilder<W>()
            .setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val POLLING = "feed-polling"
        const val POLL_NOW = "feed-poll-now"
        const val SHARE_RETRY = "share-retry"
        const val PUSH_SYNC = "push-subscription-sync"
    }
}

private val Context.container: AppContainer get() = (applicationContext as DrinkWithMeApplication).container

/** Neue Getränke der Freunde abrufen und melden (ohne Push-Distributor). */
class FeedPollingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val poller = applicationContext.container.feedPoller ?: return Result.success()
        return try {
            poller.poll()
            Result.success()
        } catch (error: SocialException.Network) {
            Result.retry()
        } catch (error: SocialException) {
            Result.success()
        }
    }
}

/** Push-Abo mit dem Server abgleichen. */
class PushSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = applicationContext.container
        val sync = container.pushSubscriptionSync ?: return Result.success()
        val outcome = sync.sync()
        // Erst ein bestätigtes Abo ersetzt den Abruf alle 15 Minuten (und ein abgelehntes schaltet ihn wieder ein).
        container.socialBackground.updatePolling()
        return when (outcome) {
            SyncOutcome.DONE -> Result.success()
            SyncOutcome.RETRY_LATER -> Result.retry()
        }
    }
}

/** Ohne Verbindung vorgemerkte Teilen-Aufträge nachholen. */
class ShareRetryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val processor = applicationContext.container.shareRetryProcessor ?: return Result.success()
        return when (processor.run()) {
            RetryOutcome.DONE -> Result.success()
            RetryOutcome.RETRY_LATER -> Result.retry()
        }
    }
}
