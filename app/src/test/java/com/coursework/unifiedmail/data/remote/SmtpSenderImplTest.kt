package com.coursework.unifiedmail.data.remote

import com.coursework.unifiedmail.data.local.MailSecurity
import javax.mail.internet.MimeMultipart
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmtpSenderImplTest {

    private val sender = SmtpSenderImpl()

    // --- buildTextPart: the actual MIME structure sent, verified without a live server ---

    @Test
    fun `a plain-text-only body is sent as a single text part`() {
        val part = sender.buildTextPart("Hello", html = null)

        assertEquals("Hello", part.content)
    }

    @Test
    fun `an html body is sent as plain+html alternative, not just the html`() {
        val part = sender.buildTextPart("Hello", html = "<p>Hello</p>")

        // Checked via the payload each body part actually carries, not isMimeType()/
        // getContentType() — those read back the part's MIME *header*, which JavaMail only
        // populates on saveChanges()/serialization (normally triggered by actually sending the
        // message), not eagerly when setText/setContent is called on a standalone part like this.
        val alternative = part.content as MimeMultipart
        assertTrue(alternative.contentType.startsWith("multipart/alternative"))
        assertEquals(2, alternative.count)
        // The plain-text part is the required RFC 2046 fallback for a client that can't render
        // HTML — losing it (e.g. sending html-only), or getting the order backwards (alternative
        // requires least-preferred first), would mean those recipients see nothing useful.
        assertEquals("Hello", alternative.getBodyPart(0).content)
        assertEquals("<p>Hello</p>", alternative.getBodyPart(1).content)
    }

    // --- send(): the error-handling contract, no fake/mock ---

    @Test
    fun `send reports a Result failure instead of throwing, even for a failure before any network call`() = runTest {
        // An invalid recipient address fails inside InternetAddress's own constructor while the
        // message is still being built — before Transport.connect ever runs — exercising the same
        // runCatching wrapping a real network failure would, without depending on a live/fake SMTP
        // server or the test environment's network policy (a sandboxed runner can make an actual
        // socket attempt hang rather than fail fast the way it would on a real device).
        val config = SmtpConfig("smtp.example.com", 465, MailSecurity.SSL_TLS, "user", "pass")
        val message = OutgoingMessage(
            fromAddress = "me@example.com",
            fromPersonal = "Me",
            to = listOf("not a valid address"),
            subject = "Hi",
            body = "Body",
        )

        val result = sender.send(config, message)

        assertTrue(result.isFailure)
    }
}
