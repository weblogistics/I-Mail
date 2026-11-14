package com.coursework.unifiedmail

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.coursework.unifiedmail.data.repository.AccountRepository
import com.coursework.unifiedmail.data.settings.AppSettings
import com.coursework.unifiedmail.data.settings.SettingsRepository
import com.coursework.unifiedmail.data.settings.ThemeMode
import com.coursework.unifiedmail.data.share.ContactShortcuts
import com.coursework.unifiedmail.ui.nav.MailNavGraph
import com.coursework.unifiedmail.ui.theme.UnifiedMailTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var accountRepository: AccountRepository

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        // A notification tap takes priority over the configured default view — the whole point
        // of tapping a "new mail in X" notification is landing on that account's Inbox, even if
        // a different account (or Unified Inbox) is normally the default.
        val deepLinkAccountId = intent?.getStringExtra(EXTRA_DEEPLINK_ACCOUNT_ID)
        // Only set when the notification tapped was for one specific message (see
        // NotificationHelper) — lands on that message directly instead of just its Inbox.
        val deepLinkFolderKey = intent?.getStringExtra(EXTRA_DEEPLINK_FOLDER_KEY)
        val deepLinkUid = intent?.getLongExtra(EXTRA_DEEPLINK_UID, -1L)?.takeIf { it >= 0 }
        // Set only by the notification's "Reply" quick action — opens straight into a reply
        // draft for that message instead of the message itself.
        val deepLinkReply = intent?.getBooleanExtra(EXTRA_DEEPLINK_REPLY, false) ?: false
        // Set only when launched via the OS share sheet (see AndroidManifest's ACTION_SEND/
        // ACTION_SEND_MULTIPLE intent-filters) — the file(s) shared in from another app.
        val shareUris = sharedFileUris(intent)
        // Set only when that share was aimed at a specific direct-share "recent people" target
        // (see ContactShortcuts) — the system redelivers the sharing intent with this extra set
        // instead of launching whatever dummy Intent the shortcut itself was built with.
        val shareRecipientAddress = ContactShortcuts.addressFromShortcutId(intent?.getStringExtra(Intent.EXTRA_SHORTCUT_ID))
        // Same tradeoff as MailApplication.onCreate: a single fast local DataStore read at
        // startup, blocked on briefly here since there's no reasonable async alternative for
        // "which account, if any, should auto-open on top of Unified Inbox" this early.
        val startAccountId = deepLinkAccountId ?: runBlocking {
            val settings = settingsRepository.settings.first()
            if (shareUris.isNotEmpty()) {
                // Which account to compose/share from is answered by defaultAccountId
                // specifically (see AppSettings) — defaultViewAccountId answers a different
                // question (which screen opens on launch) and must never override it here, even
                // when the user happens to have both set to different accounts. Falling back
                // further to the first active account only covers an install from before
                // defaultAccountId existed, where nothing has set it yet.
                settings.defaultAccountId ?: accountRepository.getAllAccountsOnce().firstOrNull { it.isActive }?.id
            } else {
                settings.defaultViewAccountId
            }
        }
        setContent {
            val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
            val darkTheme = when (settings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            UnifiedMailTheme(darkTheme = darkTheme) {
                MailNavGraph(
                    startAccountId = startAccountId,
                    startFolderKey = deepLinkFolderKey,
                    startMessageUid = deepLinkUid,
                    startReply = deepLinkReply,
                    startShareUris = shareUris,
                    startShareRecipientAddress = shareRecipientAddress,
                )
            }
        }
    }

    /** The shared file(s)' content:// Uris for an ACTION_SEND/ACTION_SEND_MULTIPLE [intent] — empty for any other action (including a plain launch, or a text-only share with no attached file). */
    private fun sharedFileUris(intent: Intent?): List<Uri> {
        val source = intent ?: return emptyList()
        return when (source.action) {
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(source, Intent.EXTRA_STREAM, Uri::class.java)?.let { listOf(it) } ?: emptyList()
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(source, Intent.EXTRA_STREAM, Uri::class.java) ?: emptyList()
            else -> emptyList()
        }
    }

    // Declared in the manifest since Milestone 1, but declaring it alone doesn't grant it on
    // API 33+ — it's a runtime permission there and new-mail notifications silently never show
    // without this request actually being made.
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        const val EXTRA_DEEPLINK_ACCOUNT_ID = "deeplink_account_id"
        const val EXTRA_DEEPLINK_FOLDER_KEY = "deeplink_folder_key"
        const val EXTRA_DEEPLINK_UID = "deeplink_uid"
        const val EXTRA_DEEPLINK_REPLY = "deeplink_reply"
    }
}
