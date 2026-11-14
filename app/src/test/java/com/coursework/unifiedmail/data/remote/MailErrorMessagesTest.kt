package com.coursework.unifiedmail.data.remote

import java.io.IOException
import java.net.UnknownHostException
import javax.mail.AuthenticationFailedException
import javax.mail.MessagingException
import org.junit.Assert.assertEquals
import org.junit.Test

class MailErrorMessagesTest {

    @Test
    fun `authentication failure points at the password`() {
        assertEquals(
            "Authentication failed — check username and password",
            describeMailError(AuthenticationFailedException("bad creds")),
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
