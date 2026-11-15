package com.coursework.unifiedmail.data.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.coursework.unifiedmail.MainActivity
import com.coursework.unifiedmail.data.repository.EmailContact
import com.coursework.unifiedmail.data.repository.MailRepository
import com.coursework.unifiedmail.ui.theme.colorForKey

/**
 * Publishes the OS "Direct Share" targets declared in res/xml/shortcuts.xml — these are what let
 * the system share sheet offer recently emailed people directly, alongside the app icon itself,
 * the way Gmail/Outlook/Samsung Mail do. Tapping one skips straight to a prefilled compose screen
 * (see MainActivity's ACTION_SEND handling) instead of making the user pick a recipient after
 * picking the app.
 */
object ContactShortcuts {
    /** Must match the <category> inside the <share-target> in res/xml/shortcuts.xml. */
    const val SHARE_TARGET_CATEGORY = "com.coursework.unifiedmail.category.SHARE_TARGET"

    private const val ID_PREFIX = "share_contact:"
    // Conservative across OEMs — ShortcutManager's own per-activity cap varies by launcher
    // (commonly 4-15), and asking for more than the device allows just fails the whole publish.
    private const val MAX_SHORTCUTS = 4
    private const val ICON_SIZE_PX = 128

    fun shortcutIdFor(address: String): String = "$ID_PREFIX${address.lowercase()}"

    /** The reverse of [shortcutIdFor] — null if [shortcutId] wasn't one of ours (e.g. the chooser's own app icon was tapped instead of a direct-share target). */
    fun addressFromShortcutId(shortcutId: String?): String? =
        shortcutId?.takeIf { it.startsWith(ID_PREFIX) }?.removePrefix(ID_PREFIX)

    /**
     * Replaces the published shortcuts with the current most-recently-emailed people. Called
     * after a successful send (recency just changed — see ComposeViewModel.send) and once at app
     * startup (so a fresh install that already synced a Sent folder shows targets without having
     * to send anything first — see MailApplication.onCreate). Always best-effort: a failure here
     * (rate limiting, a device's shortcut-count cap, ...) should never be visible to the user,
     * just a share sheet one shortcut shorter than ideal.
     *
     * Adds the new set before removing anything stale, rather than clearing everything first —
     * removeAllDynamicShortcuts() then addDynamicShortcuts() has a window where a process death
     * (or any other interruption) between the two calls leaves the share sheet with zero Direct
     * Share targets until the next successful refresh. Worst case here is a brief superset of
     * old+new shortcuts, never zero.
     */
    suspend fun refresh(context: Context, mailRepository: MailRepository) {
        runCatching {
            val recent = mailRepository.getRecentContacts(MAX_SHORTCUTS)
            val newShortcuts = recent.map { buildShortcut(context, it) }
            val newIds = newShortcuts.map { it.id }.toSet()

            ShortcutManagerCompat.addDynamicShortcuts(context, newShortcuts)
            val staleIds = ShortcutManagerCompat.getDynamicShortcuts(context).map { it.id }.filterNot { it in newIds }
            if (staleIds.isNotEmpty()) {
                ShortcutManagerCompat.removeDynamicShortcuts(context, staleIds)
            }
        }
    }

    private fun buildShortcut(context: Context, contact: EmailContact): ShortcutInfoCompat {
        val label = contact.displayName?.takeIf { it.isNotBlank() } ?: contact.address
        return ShortcutInfoCompat.Builder(context, shortcutIdFor(contact.address))
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(IconCompat.createWithBitmap(initialAvatar(label)))
            .setPerson(Person.Builder().setName(label).build())
            .setCategories(setOf(SHARE_TARGET_CATEGORY))
            // Never actually launched for a direct-share tap — the system instead redelivers the
            // original sharing intent to MainActivity with EXTRA_SHORTCUT_ID set (see
            // addressFromShortcutId) — but ShortcutInfo requires some intent regardless.
            .setIntent(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_SEND))
            .setLongLived(true)
            .build()
    }

    /** Same initial-letter, color-keyed-by-address look as SenderAvatar (see ui.theme.colorForKey), rasterized since a shortcut icon needs a Bitmap rather than a Composable. */
    private fun initialAvatar(label: String): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_SIZE_PX, ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = ICON_SIZE_PX / 2f
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorForKey(label).toArgb() }
        canvas.drawCircle(radius, radius, radius, backgroundPaint)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = ICON_SIZE_PX * 0.5f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val initial = label.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        val textY = radius - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(initial, radius, textY, textPaint)
        return bitmap
    }
}
