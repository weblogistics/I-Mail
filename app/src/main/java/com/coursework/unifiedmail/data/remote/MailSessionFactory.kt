package com.coursework.unifiedmail.data.remote

import com.coursework.unifiedmail.data.local.MailSecurity
import java.util.Properties

/**
 * Builds the JavaMail Properties/protocol-name pairs shared by connection testing, IMAP sync,
 * and (eventually) SMTP sending, so all three agree on timeouts and TLS handling.
 */
internal object MailSessionFactory {
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 10_000

    // A server-side SEARCH — especially one that scans message bodies (BodyTerm), not just
    // headers — can legitimately take far longer than a normal folder fetch on a large mailbox.
    // The default READ_TIMEOUT_MS was tight enough that a real search would trip it and come back
    // as a plain timeout failure; this gives ImapClientImpl.searchFolder its own generous budget
    // without loosening the timeout every other (normally fast) IMAP operation relies on to fail
    // quickly when a server really is unreachable.
    const val SEARCH_READ_TIMEOUT_MS = 60_000

    fun imapProtocol(security: MailSecurity): String =
        if (security == MailSecurity.SSL_TLS) "imaps" else "imap"

    fun smtpProtocol(security: MailSecurity): String =
        if (security == MailSecurity.SSL_TLS) "smtps" else "smtp"

    fun imapProperties(security: MailSecurity, readTimeoutMs: Int = READ_TIMEOUT_MS): Properties = Properties().apply {
        put("mail.store.protocol", imapProtocol(security))
        put("mail.imap.starttls.enable", (security == MailSecurity.STARTTLS).toString())
        // Without "required" too, "enable" only ever upgrades opportunistically — a server that
        // silently stops offering STARTTLS (stripped by an on-path attacker, or just
        // misconfigured) would fall back to an unencrypted connection instead of failing.
        put("mail.imap.starttls.required", (security == MailSecurity.STARTTLS).toString())
        // Deliberately no "ssl.trust" override here: that trusted *any* certificate for *any*
        // host, which defeats TLS entirely (a trivial MITM target on hostile networks). Leaving
        // it unset means the JVM's default trust store validates certs/hostnames normally.
        put("mail.imap.connectiontimeout", CONNECT_TIMEOUT_MS.toString())
        put("mail.imap.timeout", readTimeoutMs.toString())
        put("mail.imaps.connectiontimeout", CONNECT_TIMEOUT_MS.toString())
        put("mail.imaps.timeout", readTimeoutMs.toString())
    }

    fun smtpProperties(security: MailSecurity): Properties = Properties().apply {
        put("mail.transport.protocol", smtpProtocol(security))
        put("mail.smtp.auth", "true")
        put("mail.smtp.starttls.enable", (security == MailSecurity.STARTTLS).toString())
        // See imapProperties — same silent-downgrade risk without "required".
        put("mail.smtp.starttls.required", (security == MailSecurity.STARTTLS).toString())
        // See imapProperties — same reasoning for not trusting every certificate.
        put("mail.smtp.connectiontimeout", CONNECT_TIMEOUT_MS.toString())
        put("mail.smtp.timeout", READ_TIMEOUT_MS.toString())
        put("mail.smtps.connectiontimeout", CONNECT_TIMEOUT_MS.toString())
        put("mail.smtps.timeout", READ_TIMEOUT_MS.toString())
    }
}
