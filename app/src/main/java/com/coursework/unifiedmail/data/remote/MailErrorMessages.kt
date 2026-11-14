package com.coursework.unifiedmail.data.remote

import java.io.IOException
import java.net.UnknownHostException
import javax.mail.AuthenticationFailedException
import javax.mail.MessagingException

/**
 * Turns a raw JavaMail/IO exception into a message worth showing a user — distinguishing "your
 * password is wrong" from "you're offline" from "the server rejected this," which otherwise all
 * surface as similarly unhelpful low-level exception text.
 *
 * [host] (the IMAP/SMTP host actually being connected to, when the caller has one handy) lets an
 * [AuthenticationFailedException] get a provider-specific hint instead of the generic message —
 * Gmail and Outlook/Hotmail/Live both reject a real account password outright for IMAP/SMTP and
 * require an App Password (or OAuth) instead, which otherwise looks identical to a plain typo to
 * a first-time setup and to this exact exception type.
 */
fun describeMailError(throwable: Throwable, host: String? = null): String = when (throwable) {
    is AuthenticationFailedException -> authenticationFailedMessage(host)
    is UnknownHostException -> "Couldn't reach the server — check the host name and your network connection"
    is IOException -> "Network error — check your connection and try again"
    is MessagingException -> throwable.message ?: "Mail server error"
    else -> throwable.message ?: "Unexpected error"
}

private fun authenticationFailedMessage(host: String?): String {
    val normalizedHost = host?.lowercase().orEmpty()
    return when {
        "gmail.com" in normalizedHost -> "Authentication failed — Gmail doesn't accept your regular password here. " +
            "Create an App Password in your Google Account's security settings (under 2-Step Verification) and use that instead."
        "office365.com" in normalizedHost || "outlook.com" in normalizedHost -> "Authentication failed — Outlook/Hotmail/Live doesn't accept your " +
            "regular password here. Create an App Password in your Microsoft account's security settings and use that instead."
        else -> "Authentication failed — check username and password"
    }
}
