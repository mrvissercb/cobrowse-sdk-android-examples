package io.cobrowse.sample.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.cobrowse.CobrowseIO
import io.cobrowse.sample.R
import io.cobrowse.sample.data.LoginRepository
import io.cobrowse.sample.data.model.LoggedInUser
import io.cobrowse.sample.ui.BaseViewModel

/**
 * View-Model for [AccountFragment].
 */
class AccountViewModel(private val loginRepository: LoginRepository) : BaseViewModel() {

    private val _logoutResult = MutableLiveData<LogoutResult>()
    val logoutResult: LiveData<LogoutResult> = _logoutResult

    private val _sessionCodeResult = MutableLiveData<String>()
    val sessionCodeResult: LiveData<String> = _sessionCodeResult

    val user: LoggedInUser?
        get() = loginRepository.user

    fun logOut() {
        if (loginRepository.logout()) {
            _logoutResult.value = LogoutResult(success = true)
        } else {
            _logoutResult.value = LogoutResult(error = R.string.logout_failed)
        }
    }

    fun requestSessionCode() {
        CobrowseIO.instance().createSession { error, session ->
            if (error !== null) {
                System.err.println("Error creating session")
                error.printStackTrace()
            }

            val code = session?.code()
            if (code.isNullOrEmpty()) {
                _sessionCodeResult.value = ""
            } else {
                // We always assume the code has only 6 digits
                _sessionCodeResult.value = "${code.substring(0, 3)} - ${code.substring(3, 6)}"
            }
        }
    }
}