package com.coursework.unifiedmail.ui.drafts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coursework.unifiedmail.data.local.DraftEntity
import com.coursework.unifiedmail.data.local.OutboxEntity
import com.coursework.unifiedmail.data.repository.MailRepository
import com.coursework.unifiedmail.data.repository.SendOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DraftListViewModel @Inject constructor(
    private val mailRepository: MailRepository,
) : ViewModel() {

    val drafts: StateFlow<List<DraftEntity>> = mailRepository.observeDrafts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // See MailRepository.observeFailedOutbox — a message that permanently failed to send
    // (ComposeViewModel.send already shows "sent" the moment it's queued) used to disappear into
    // a background retry loop with no way for the user to even learn it didn't go out.
    val failedOutbox: StateFlow<List<OutboxEntity>> = mailRepository.observeFailedOutbox()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Which failed item (by id), if any, is currently being retried — disables its own Retry
    // button without blocking the others.
    private val _retryingId = MutableStateFlow<String?>(null)
    val retryingId: StateFlow<String?> = _retryingId.asStateFlow()

    // One-shot confirmation/failure for a manual retry — shown as a Snackbar.
    private val _infoMessage = MutableStateFlow<String?>(null)
    val infoMessage: StateFlow<String?> = _infoMessage.asStateFlow()

    fun deleteDraft(draftId: String) {
        viewModelScope.launch {
            mailRepository.deleteDraft(draftId)
        }
    }

    fun retry(outboxId: String) {
        if (_retryingId.value != null) return
        viewModelScope.launch {
            _retryingId.value = outboxId
            when (val result = mailRepository.retryOutboxItem(outboxId)) {
                is SendOutcome.Success -> _infoMessage.value = "Message sent"
                is SendOutcome.Failure -> _infoMessage.value = result.reason
            }
            _retryingId.value = null
        }
    }

    fun discard(outboxId: String) {
        viewModelScope.launch { mailRepository.discardOutboxItem(outboxId) }
    }

    fun dismissInfoMessage() {
        _infoMessage.value = null
    }
}
