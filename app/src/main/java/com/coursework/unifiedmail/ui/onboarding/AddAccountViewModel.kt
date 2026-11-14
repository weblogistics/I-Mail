package com.coursework.unifiedmail.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coursework.unifiedmail.data.local.MailSecurity
import com.coursework.unifiedmail.data.remote.ImapConfig
import com.coursework.unifiedmail.data.remote.MailConnectionTester
import com.coursework.unifiedmail.data.remote.MailServerDefaults
import com.coursework.unifiedmail.data.remote.MailTestResult
import com.coursework.unifiedmail.data.remote.SmtpConfig
import com.coursework.unifiedmail.data.repository.AccountRepository
import com.coursework.unifiedmail.data.repository.NewAccount
import com.coursework.unifiedmail.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ConnectionCheckStatus { IDLE, TESTING, SUCCESS, FAILED }

data class AddAccountUiState(
    val displayName: String = "",
    val emailAddress: String = "",
    val username: String = "",
    val password: String = "",
    val imapHost: String = "",
    val imapPort: String = "993",
    val imapSecurity: MailSecurity = MailSecurity.SSL_TLS,
    val smtpHost: String = "",
    val smtpPort: String = "465",
    val smtpSecurity: MailSecurity = MailSecurity.SSL_TLS,
    val connectionStatus: ConnectionCheckStatus = ConnectionCheckStatus.IDLE,
    val connectionMessage: String? = null,
    val isSaving: Boolean = false,
    val savedAccountId: String? = null,
) {
    /** Enough entered to attempt [AddAccountViewModel.testConnection] — doesn't require having passed one yet. */
    val canTestConnection: Boolean
        get() = displayName.isNotBlank() &&
            emailAddress.isNotBlank() &&
            username.isNotBlank() &&
            password.isNotBlank() &&
            imapHost.isNotBlank() &&
            imapPort.toIntOrNull() != null &&
            smtpHost.isNotBlank() &&
            smtpPort.toIntOrNull() != null

    /**
     * Also requires a passing connection test, unlike [canTestConnection] — a typo'd password or
     * host used to save an account that looked fine and only failed invisibly on its first
     * background sync. Any further edit to a connection-relevant field resets [connectionStatus]
     * back to IDLE (see AddAccountViewModel's on*Change functions), so a stale pass from before
     * that edit can't satisfy this.
     */
    val canSave: Boolean
        get() = canTestConnection && connectionStatus == ConnectionCheckStatus.SUCCESS
}

@HiltViewModel
class AddAccountViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val mailConnectionTester: MailConnectionTester,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddAccountUiState())
    val uiState: StateFlow<AddAccountUiState> = _uiState.asStateFlow()

    fun onDisplayNameChange(value: String) = _uiState.update { it.copy(displayName = value) }

    /**
     * Best-effort autodiscovery: fills in host/port/security (and username, if still blank) from
     * the email domain — see MailServerDefaults. Only while both host fields are still blank, so
     * typing further into an address never clobbers host details the user already entered or
     * edited by hand.
     */
    fun onEmailAddressChange(value: String) {
        _uiState.update { current ->
            if (current.imapHost.isNotBlank() || current.smtpHost.isNotBlank()) {
                return@update current.copy(emailAddress = value)
            }
            val guess = MailServerDefaults.guessForEmail(value) ?: return@update current.copy(emailAddress = value)
            current.copy(
                emailAddress = value,
                username = current.username.ifBlank { value },
                imapHost = guess.imapHost,
                imapPort = guess.imapPort.toString(),
                imapSecurity = guess.imapSecurity,
                smtpHost = guess.smtpHost,
                smtpPort = guess.smtpPort.toString(),
                smtpSecurity = guess.smtpSecurity,
            ).invalidateConnectionCheck()
        }
    }

    fun onUsernameChange(value: String) = _uiState.update { it.copy(username = value).invalidateConnectionCheck() }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value).invalidateConnectionCheck() }
    fun onImapHostChange(value: String) = _uiState.update { it.copy(imapHost = value).invalidateConnectionCheck() }
    fun onImapPortChange(value: String) = _uiState.update { it.copy(imapPort = value).invalidateConnectionCheck() }
    fun onImapSecurityChange(value: MailSecurity) =
        _uiState.update { it.copy(imapSecurity = value, imapPort = MailServerDefaults.imapPort(value).toString()).invalidateConnectionCheck() }
    fun onSmtpHostChange(value: String) = _uiState.update { it.copy(smtpHost = value).invalidateConnectionCheck() }
    fun onSmtpPortChange(value: String) = _uiState.update { it.copy(smtpPort = value).invalidateConnectionCheck() }
    fun onSmtpSecurityChange(value: MailSecurity) =
        _uiState.update { it.copy(smtpSecurity = value, smtpPort = MailServerDefaults.smtpPort(value).toString()).invalidateConnectionCheck() }

    /** A previous test's result stops being trustworthy once a connection-relevant field changes again — back to IDLE so [AddAccountUiState.canSave] can't be satisfied by a now-stale pass. */
    private fun AddAccountUiState.invalidateConnectionCheck(): AddAccountUiState =
        if (connectionStatus == ConnectionCheckStatus.IDLE) this else copy(connectionStatus = ConnectionCheckStatus.IDLE, connectionMessage = null)

    fun testConnection() {
        val state = _uiState.value
        val imapPort = state.imapPort.toIntOrNull()
        val smtpPort = state.smtpPort.toIntOrNull()
        if (imapPort == null || smtpPort == null) {
            _uiState.update {
                it.copy(connectionStatus = ConnectionCheckStatus.FAILED, connectionMessage = "Port must be a number")
            }
            return
        }

        _uiState.update { it.copy(connectionStatus = ConnectionCheckStatus.TESTING, connectionMessage = null) }

        viewModelScope.launch {
            val imapResult = mailConnectionTester.testImapLogin(
                ImapConfig(state.imapHost, imapPort, state.imapSecurity, state.username, state.password),
            )
            if (imapResult is MailTestResult.Failure) {
                _uiState.update {
                    it.copy(connectionStatus = ConnectionCheckStatus.FAILED, connectionMessage = "IMAP: ${imapResult.reason}")
                }
                return@launch
            }

            val smtpResult = mailConnectionTester.testSmtpLogin(
                SmtpConfig(state.smtpHost, smtpPort, state.smtpSecurity, state.username, state.password),
            )
            if (smtpResult is MailTestResult.Failure) {
                _uiState.update {
                    it.copy(connectionStatus = ConnectionCheckStatus.FAILED, connectionMessage = "SMTP: ${smtpResult.reason}")
                }
                return@launch
            }

            _uiState.update {
                it.copy(connectionStatus = ConnectionCheckStatus.SUCCESS, connectionMessage = "Connection successful")
            }
        }
    }

    fun saveAccount() {
        val state = _uiState.value
        val imapPort = state.imapPort.toIntOrNull() ?: return
        val smtpPort = state.smtpPort.toIntOrNull() ?: return
        if (!state.canSave) return

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val saved = accountRepository.addAccount(
                NewAccount(
                    displayName = state.displayName,
                    emailAddress = state.emailAddress,
                    username = state.username,
                    password = state.password,
                    imapHost = state.imapHost,
                    imapPort = imapPort,
                    imapSecurity = state.imapSecurity,
                    smtpHost = state.smtpHost,
                    smtpPort = smtpPort,
                    smtpSecurity = state.smtpSecurity,
                ),
            )
            // First account ever (or the only one left after others were removed) becomes the
            // default send account automatically — otherwise compose-from-Unified-Inbox and the
            // OS share sheet would have no account to act as until the user went and set one
            // manually in Settings. A second+ account never overrides whatever's already chosen.
            if (accountRepository.getAllAccountsOnce().size == 1) {
                settingsRepository.setDefaultAccountId(saved.id)
            }
            _uiState.update { it.copy(isSaving = false, savedAccountId = saved.id) }
        }
    }
}
