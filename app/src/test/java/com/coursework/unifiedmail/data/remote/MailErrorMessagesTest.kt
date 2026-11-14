package com.coursework.unifiedmail.data.remote

import java.io.IOException
import java.net.UnknownHostException
import javax.mail.AuthenticationFailedException
import javax.mail.MessagingException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MailErrorMessagesTest {

    @Test
    fun `authentication failure points at the password when no host is given`() {
        assertEquals(
            "Authentication failed — check username and password",
            describeMailError(AuthenticationFailedException("bad creds")),
        )
    }

    @Test
    fun `authentication failure against Gmail hints at an App Password`() {
        val message = describeMailError(AuthenticationFailedException("bad creds"), host = "imap.gmail.com")

        assertTrue(message.contains("App Password"))
        assertTrue(message.contains("Gmail"))
    }

    @Test
    fun `authentication failure against Outlook's office365 host hints at an App Password`() {
        // Outlook.com, Hotmail, Live, and MSN all resolve to the same office365.com host via
        // MailServerDefaults' known-provider guesses — one check covers all four.
        val message = describeMailError(AuthenticationFailedException("bad creds"), host = "outlook.office365.com")

        assertTrue(message.contains("App Password"))
    }

    @Test
    fun `authentication failure against an unrecognized host stays generic`() {
        assertEquals(
            "Authentication failed — check username and password",
            describeMailError(AuthenticationFailedException("bad creds"), host = "mail.mycompany.example"),
        )
    }

    @Test
    fun `unknown host points at the host name and network`() {
        assertEquals(
            "Couldn't reach the server — check the host name and your network connection",
            describeMailError(UnknownHostException("imap.example.invalid")),
        )
    }

    @Test
    fun `a plain IO failure is reported as a network error`() {
        assertEquals(
            "Network error — check your connection and try again",
            describeMailError(IOException("socket closed")),
        )
    }

    @Test
    fun `a generic messaging exception surfaces its own message`() {
        assertEquals("Mailbox does not exist", describeMailError(MessagingException("Mailbox does not exist")))
    }

    @Test
    fun `a messaging exception with no message falls back to a generic label`() {
        assertEquals("Mail server error", describeMailError(MessagingException()))
    }

    @Test
    fun `any other throwable falls back to its own message or a generic label`() {
        assertEquals("boom", describeMailError(RuntimeException("boom")))
        assertEquals("Unexpected error", describeMailError(RuntimeException()))
    }
}
