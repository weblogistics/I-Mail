# Follow-up from the code/UX audit (2026-11-14)

Already fixed: TLS certificate validation was disabled (`ssl.trust = "*"`) and STARTTLS could
silently downgrade to plaintext — both fixed in `data/remote/MailSessionFactory.kt`.

Everything below is still open unless checked off. Delete whatever you decide not to do.

## Reliability

- [x] **Send failures are invisible.** Fixed: `MailRepository.observeFailedOutbox`/
      `retryOutboxItem`/`discardOutboxItem` + a "Couldn't send" section on the Drafts screen
      (`DraftListScreen.kt`) showing the failure reason with Retry/Discard. Tested
      (`MailRepositoryTest`) and pushed. Not done: no push notification/badge for it yet, and
      Retry/Discard only — no "Edit" (the draft row is already gone by the time something
      reaches the outbox, so there's nothing to reopen; Discard + compose a fresh message is the
      current equivalent).
- [ ] **Gmail/Outlook.com onboarding fails with no useful hint.** Both require an App
      Password/OAuth today; a real password just fails with a generic "Authentication failed —
      check username and password" (`MailErrorMessages.kt` / `describeMailError`). Detect the
      known-provider case and show a specific hint.
- [x] **Save isn't gated on a successful connection test.** Fixed: `AddAccountUiState.canSave`
      now also requires `connectionStatus == SUCCESS`; any further edit to a connection-relevant
      field resets that back to IDLE so a stale pass can't satisfy it. Tested
      (`AddAccountUiStateTest`) and pushed.
- [x] No test coverage on the send path — partially fixed. Added `SmtpSenderImplTest` (MIME
      construction + the error-wrapping contract via a pre-network failure) and
      `MailErrorMessagesTest`. **Still untested, and likely to stay that way without new test
      infrastructure:** `MailConnectionTester`'s two connect functions have no non-network code
      path to exercise (connecting *is* the entire function, unlike `SmtpSender.send` which
      builds a message object first) — and a real socket attempt hangs unpredictably in this
      project's test-running environment rather than failing fast, so a reliable test here needs
      either a mocking framework (Mockito/MockK — not currently a dependency) or a fake/local
      mail server (e.g. GreenMail). `CredentialStore`/`MailIdleService`/`SyncScheduler` remain
      untested too — they depend on real Android framework classes (EncryptedSharedPreferences,
      a foreground Service, WorkManager) that need Robolectric or instrumented tests, neither of
      which this project has set up.
- [ ] `MailIdleService.runIdleLoop` retries forever even when the failure is a missing/invalid
      credential that can never succeed on retry — should stop (or back off much further) on
      that specific failure instead of treating it like a transient network blip.
- [ ] Every single IMAP action (flag, move, open one attachment) opens a brand-new
      connect+TLS+login round trip rather than reusing a short-lived connection — correct, just
      slower than necessary on bad networks. Worth a connection-reuse pass if responsiveness on
      slow networks becomes a complaint.
- [ ] `folderSyncMutexes` in `MailRepository.kt` grows unbounded — entries aren't evicted when an
      account/folder is deleted. Minor; bounded by realistic account/folder counts in practice.

## UX

- [ ] Manual IMAP/SMTP host/port fields are always shown on Add Account even though
      auto-detection already fills them for known providers. Collapse behind a "Manual setup"
      disclosure, pre-filled but hidden by default.
- [ ] "Default view" and "Default send account" read as near-identical rows in Settings with no
      distinguishing caption, unlike other settings on that screen. Add the same short caption
      style used elsewhere.
- [ ] Per-account signature is only discoverable by opening Edit Account — not hinted at from
      Settings or Compose.
- [ ] Zero-account empty state says "open the menu to add one" instead of offering an inline
      "Add account" button directly in the empty state.
- [ ] Folder list's empty state may not distinguish "still loading" from "genuinely empty" the
      way the inbox skeleton does for messages — worth mirroring that pattern if not already
      covered.
