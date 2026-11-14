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
- [x] **`MailIdleService.runIdleLoop` retries forever on a missing credential.** Fixed: a
      dedicated `MissingCredentialsException` + 1-hour backoff instead of the usual max-5-minute
      one — stops hammering reconnect for an account that can never succeed until the user
      re-enters a password, while still recovering on its own if they do. Pushed (not unit
      tested — `MailIdleService` depends on a real Android foreground `Service`; see the test
      coverage item above for why that needs Robolectric/instrumented tests this project doesn't
      have set up).
- [ ] Every single IMAP action (flag, move, open one attachment) opens a brand-new
      connect+TLS+login round trip rather than reusing a short-lived connection — correct, just
      slower than necessary on bad networks. Worth a connection-reuse pass if responsiveness on
      slow networks becomes a complaint. **Strategy written up (not implemented) — see chat.**
- [x] **`folderSyncMutexes` grows unbounded.** Fixed: evicted alongside everything else
      `deleteAccountAndCache` already cleans up for a removed account. Pushed.

## UX

- [ ] Manual IMAP/SMTP host/port fields are always shown on Add Account even though
      auto-detection already fills them for known providers. Collapse behind a "Manual setup"
      disclosure, pre-filled but hidden by default.
- [x] **"Default view"/"Default send account" read as near-identical rows.** Fixed: added a
      caption slot to `SettingRow`, used for both. Pushed.
- [x] **Per-account signature isn't hinted at anywhere.** Fixed: a one-line pointer in Settings
      ("each account's signature can be set from Manage accounts in the drawer menu"), right
      below the account-related settings. Pushed.
- [x] **Zero-account empty state has no inline action.** Fixed: `EmptyState` now takes an
      optional `actionLabel`/`onAction`; Unified Inbox's empty state offers "Add account"
      directly, skipping the account-list screen. Pushed.
- [x] **Folder list's loading vs. empty state.** Checked — already handled correctly:
      `FolderListViewModel._isLoading` defaults to `true` and `FolderListScreen` only shows "No
      folders" once loading has actually finished with no error. No change needed.
