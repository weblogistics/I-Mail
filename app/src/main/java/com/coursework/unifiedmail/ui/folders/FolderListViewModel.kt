package com.coursework.unifiedmail.ui.folders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coursework.unifiedmail.data.local.AccountEntity
import com.coursework.unifiedmail.data.local.FolderEntity
import com.coursework.unifiedmail.data.repository.AccountRepository
import com.coursework.unifiedmail.data.repository.MailRepository
import com.coursework.unifiedmail.data.repository.SyncOutcome
import com.coursework.unifiedmail.domain.folders.FolderTree
import com.coursework.unifiedmail.domain.folders.FolderTreeNode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FolderListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mailRepository: MailRepository,
    private val accountRepository: AccountRepository,
) : ViewModel() {

    val accountId: String = checkNotNull(savedStateHandle["accountId"])

    private val _folders = MutableStateFlow<List<FolderEntity>>(emptyList())

    private val _account = MutableStateFlow<AccountEntity?>(null)

    // Empty by default — the tree starts fully expanded, same as most mail apps' folder pickers.
    private val _collapsedFolderFullNames = MutableStateFlow<Set<String>>(emptySet())

    /** Depth-first, respecting collapse state — ready for the LazyColumn to render directly. */
    val visibleFolders: StateFlow<List<FolderTreeNode>> = combine(_folders, _collapsedFolderFullNames) { folders, collapsed ->
        FolderTree.flattenVisible(FolderTree.build(folders), collapsed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Inbox/Sent/Drafts/Outbox plus this account's Trash/Archive/Spam — the same shortcut set the
     * drawer surfaces for an expanded account (see AppDrawerContent), shown here as its own
     * section above the full tree rather than leaving the user to find them wherever they happen
     * to fall alphabetically.
     */
    val commonFolders: StateFlow<List<FolderEntity>> = combine(_folders, _account) { folders, account ->
        FolderTree.commonFolders(folders, account?.trashFolderFullName, account?.archiveFolderFullName, account?.spamFolderFullName, account?.sentFolderFullName)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val collapsedFolderFullNames: StateFlow<Set<String>> = _collapsedFolderFullNames.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        viewModelScope.launch { _account.value = accountRepository.getAccount(accountId) }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            // Folder discovery piggybacks on a normal INBOX sync (it always refreshes the
            // account's folder list first) rather than adding a separate "just list folders"
            // round trip.
            when (val result = mailRepository.syncAccount(accountId)) {
                is SyncOutcome.Failure -> _error.value = result.reason
                is SyncOutcome.Success -> Unit
            }
            _folders.value = mailRepository.getFolders(accountId)
            _isLoading.value = false
        }
    }

    fun toggleExpanded(fullName: String) {
        _collapsedFolderFullNames.update { current ->
            if (fullName in current) current - fullName else current + fullName
        }
    }
}
