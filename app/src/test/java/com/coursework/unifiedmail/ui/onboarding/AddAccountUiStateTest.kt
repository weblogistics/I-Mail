package com.coursework.unifiedmail.ui.onboarding

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddAccountUiStateTest {

    private val complete = AddAccountUiState(
        displayName = "Work",
        emailAddress = "me@example.com",
        username = "me",
        password = "secret",
        imapHost = "imap.example.com",
        imapPort = "993",
        smtpHost = "smtp.example.com",
        smtpPort = "465",
    )

    @Test
    fun `canTestConnection is true once every field is filled in, before any test has run`() {
        assertTrue(complete.copy(connectionStatus = ConnectionCheckStatus.IDLE).canTestConnection)
    }

    @Test
    fun `canTestConnection is false while any required field is blank`() {
        assertFalse(complete.copy(displayName = "").canTestConnection)
        assertFalse(complete.copy(imapHost = "").canTestConnection)
        assertFalse(complete.copy(imapPort = "not a number").canTestConnection)
        assertFalse(complete.copy(smtpPort = "").canTestConnection)
    }

    @Test
    fun `canSave is false until the connection test has actually passed`() {
        assertFalse(complete.copy(connectionStatus = ConnectionCheckStatus.IDLE).canSave)
        assertFalse(complete.copy(connectionStatus = ConnectionCheckStatus.TESTING).canSave)
        assertFalse(complete.copy(connectionStatus = ConnectionCheckStatus.FAILED).canSave)
    }

    @Test
    fun `canSave is true only once fields are complete AND the test succeeded`() {
        assertTrue(complete.copy(connectionStatus = ConnectionCheckStatus.SUCCESS).canSave)
    }

    @Test
    fun `a SUCCESS status alone isn't enough if a required field is missing`() {
        // Guards against a bug class in the opposite direction: a stale SUCCESS surviving a field
        // being cleared out shouldn't let Save through with incomplete data either.
        assertFalse(complete.copy(password = "", connectionStatus = ConnectionCheckStatus.SUCCESS).canSave)
    }
}
