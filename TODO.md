# Follow-up from the code/UX audit (2026-11-14)

Already fixed: TLS certificate validation was disabled (`ssl.trust = "*"`) and STARTTLS could
silently downgrade to plaintext — both fixed in `data/remote/MailSessionFactory.kt`.

Everything below is still open. Delete or check off whatever you decide not to do.

## Reliability

- [ ] **Send failures are invisible.** `ComposeViewModel.send()` marks the UI "sent" as soon as
      the message is queued; the real delivery happens later via the outbox worker. A permanent
      failure is recorded as `OutboxStatus.FAILED` with a reason, but nothing in the UI ever
      shows it — the user just believes it sent. Needs an Outbox/"Couldn't send" view with the
      failure reason and a Retry/Edit action, surfaced at minimum as a badge or notification.
- [ ] **Gmail/Outlook.com onboarding fails with no useful hint.** Both require an App
      Password/OAuth today; a real password just fails with a generic "Authentication failed —
      check username and password" (`MailErrorMessages.kt` / `describeMailError`). Detect the
      known-provider case and show a specific hint.
- [ ] **Save isn't gated on a successful connection test.** `AddAccountViewModel.canSave` only
      checks fields are non-blank — a typo'd password creates an account that looks fine and
      only fails invisibly on the first background sync. Require (or auto-run) a passing test
      before the account is actually saved.
- [ ] No test coverage on the send path (`SmtpSender`, `MailConnectionTester`) or on
      `CredentialStore`/`MailIdleService`/`SyncScheduler` — the most consequential and most
      concurrency-sensitive code has the least safety net.
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
