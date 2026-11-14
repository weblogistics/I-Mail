package com.coursework.unifiedmail.ui.drafts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.coursework.unifiedmail.data.local.DraftEntity
import com.coursework.unifiedmail.data.local.OutboxEntity
import com.coursework.unifiedmail.domain.richtext.RichText
import com.coursework.unifiedmail.ui.components.EmptyState
import com.coursework.unifiedmail.ui.components.SectionLabel
import com.coursework.unifiedmail.ui.components.formatRelativeDate
import com.coursework.unifiedmail.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftListScreen(
    onBack: () -> Unit,
    onDraftClick: (accountId: String, draftId: String) -> Unit,
    viewModel: DraftListViewModel = hiltViewModel(),
) {
    val drafts by viewModel.drafts.collectAsState()
    val failedOutbox by viewModel.failedOutbox.collectAsState()
    val retryingId by viewModel.retryingId.collectAsState()
    val infoMessage by viewModel.infoMessage.collectAsState()
    var draftPendingDelete by remember { mutableStateOf<DraftEntity?>(null) }
    var outboxPendingDiscard by remember { mutableStateOf<OutboxEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(infoMessage) {
        val message = infoMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.dismissInfoMessage()
    }

    draftPendingDelete?.let { draft ->
        AlertDialog(
            onDismissRequest = { draftPendingDelete = null },
            title = { Text("Delete draft?") },
            text = { Text("\"${draft.subject.ifBlank { "(no subject)" }}\" will be permanently deleted. This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDraft(draft.id)
                        draftPendingDelete = null
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { draftPendingDelete = null }) { Text("Cancel") }
            },
        )
    }

    outboxPendingDiscard?.let { item ->
        AlertDialog(
            onDismissRequest = { outboxPendingDiscard = null },
            title = { Text("Discard this message?") },
            text = { Text("\"${item.subject.ifBlank { "(no subject)" }}\" will be permanently discarded, not sent. This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.discard(item.id)
                        outboxPendingDiscard = null
                    },
                ) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { outboxPendingDiscard = null }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Drafts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (drafts.isEmpty() && failedOutbox.isEmpty()) {
            EmptyState(icon = Icons.Filled.Drafts, message = "No drafts", modifier = Modifier.padding(padding))
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                if (failedOutbox.isNotEmpty()) {
                    item { SectionLabel(text = "Couldn't send", modifier = Modifier.padding(Spacing.md)) }
                    items(failedOutbox, key = { it.id }) { outboxItem ->
                        FailedOutboxRow(
                            item = outboxItem,
                            isRetrying = retryingId == outboxItem.id,
                            onRetry = { viewModel.retry(outboxItem.id) },
                            onDiscard = { outboxPendingDiscard = outboxItem },
                        )
                        HorizontalDivider()
                    }
                    if (drafts.isNotEmpty()) {
                        item { SectionLabel(text = "Drafts", modifier = Modifier.padding(Spacing.md)) }
                    }
                }
                items(drafts, key = { it.id }) { draft ->
                    DraftRow(
                        draft = draft,
                        onClick = { onDraftClick(draft.accountId, draft.id) },
                        onDelete = { draftPendingDelete = draft },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

/** A permanently-failed send — see MailRepository.observeFailedOutbox. Offers Retry (tries again right away) and Discard (gives up on it for good), since this message never made it to any draft to just reopen and fix. */
@Composable
private fun FailedOutboxRow(item: OutboxEntity, isRetrying: Boolean, onRetry: () -> Unit, onDiscard: () -> Unit) {
    ListItem(
        modifier = Modifier.fillMaxWidth(),
        leadingContent = {
            Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        },
        overlineContent = { Text("To: ${item.toAddresses}", maxLines = 1) },
        headlineContent = { Text(item.subject.ifBlank { "(no subject)" }, maxLines = 1) },
        supportingContent = {
            // No maxLines cap — unlike a typical short failure reason, the Gmail/Outlook
            // App Password hint (see MailErrorMessages.describeMailError) is multiple sentences
            // long, and this is the one place a user actually needs to read the whole thing to
            // fix it rather than just seeing "something went wrong."
            Text(
                text = item.errorMessage ?: "Couldn't send",
                color = MaterialTheme.colorScheme.error,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRetry, enabled = !isRetrying) {
                    if (isRetrying) {
                        CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = "Retry sending")
                    }
                }
                IconButton(onClick = onDiscard, enabled = !isRetrying) {
                    Icon(Icons.Filled.Delete, contentDescription = "Discard")
                }
            }
        },
    )
}

@Composable
private fun DraftRow(draft: DraftEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    val bodyPreview = RichText.plainTextOf(RichText.fromHtml(draft.bodyHtml)).replace('\n', ' ').trim()
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        headlineContent = { Text(draft.subject.ifBlank { "(no subject)" }, maxLines = 1) },
        supportingContent = {
            Text(
                text = listOfNotNull(draft.to.takeIf { it.isNotBlank() }, bodyPreview.takeIf { it.isNotBlank() }).joinToString(" — "),
                maxLines = 1,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatRelativeDate(draft.updatedAtEpochMillis))
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete draft")
                }
            }
        },
    )
}
