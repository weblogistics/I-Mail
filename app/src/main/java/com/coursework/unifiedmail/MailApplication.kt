package com.coursework.unifiedmail

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.coursework.unifiedmail.data.repository.MailRepository
import com.coursework.unifiedmail.data.settings.SettingsRepository
import com.coursework.unifiedmail.data.share.ContactShortcuts
import com.coursework.unifiedmail.sync.MailIdleService
import com.coursework.unifiedmail.sync.NotificationHelper
import com.coursework.unifiedmail.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltAndroidApp
class MailApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var mailRepository: MailRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createChannel()
        // A single fast local DataStore read at startup — acceptable to block briefly on here,
        // there's no reasonable async alternative for "what interval should the very first
        // periodic work request use" this early in the process lifecycle.
        val settings = runBlocking { settingsRepository.settings.first() }
        // WorkManager can't schedule a 0-minute period, and it stays enabled as a backstop even
        // when push is active — so "on arrival" still gets a real (default-interval) polling
        // fallback underneath it, not just the IDLE connection alone.
        val workManagerInterval = if (settings.isPushSync) SyncScheduler.DEFAULT_SYNC_INTERVAL_MINUTES else settings.syncIntervalMinutes
        SyncScheduler.schedulePeriodicSync(this, workManagerInterval)
        if (settings.isPushSync) {
            MailIdleService.start(this)
        }
        // Best-effort: populates the OS share sheet's direct-share "recent people" targets from
        // whatever Sent mail is already cached, so a fresh install that's already synced doesn't
        // need to send anything first (see ComposeViewModel.send for the other refresh trigger).
        CoroutineScope(Dispatchers.IO).launch { ContactShortcuts.refresh(this@MailApplication, mailRepository) }
    }
}
