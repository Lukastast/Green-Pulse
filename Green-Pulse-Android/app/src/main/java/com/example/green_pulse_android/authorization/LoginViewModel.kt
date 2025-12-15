package com.example.green_pulse_android.authorization

import android.util.Log
import androidx.credentials.Credential
import androidx.credentials.CustomCredential
import com.example.green_pulse_android.GreenPulseViewModel
import com.example.green_pulse_android.firebase.AccountService
import com.example.green_pulse_android.helpers.AuthErrorMapper
import com.example.green_pulse_android.helpers.AuthState
import com.example.green_pulse_android.helpers.ERROR_TAG
import com.example.green_pulse_android.helpers.HOME_SCREEN
import com.example.green_pulse_android.helpers.LOGIN_SCREEN
import com.example.green_pulse_android.helpers.UNEXPECTED_CREDENTIAL
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val accountService: AccountService
) : GreenPulseViewModel() {
    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun updateEmail(newEmail: String) {
        _email.value = newEmail
        resetState()
    }

    fun updatePassword(newPassword: String) {
        _password.value = newPassword
        resetState()
    }

    private fun resetState() {
        if (_authState.value is AuthState.Error) {
            _authState.value = AuthState.Idle
        }
    }


    fun onSignInClick(openAndPopUp: (String, String) -> Unit) {
        if (_email.value.isBlank() || _password.value.isBlank()) {
            _authState.value = AuthState.Error("Please fill all fields")
            return
        }
        if (!_email.value.isValidEmail()) {
            _authState.value = AuthState.Error("Invalid email format")
            return
        }

        _authState.value = AuthState.Loading

        launchCatching(
            onError = { e ->
                val errorMessage = AuthErrorMapper.mapError(
                    e,
                    mappings = AuthErrorMapper.loginErrorMappings,
                    errorTag = ERROR_TAG
                )
                _authState.value = AuthState.Error(errorMessage)
            }
        ) {
            accountService.signInWithEmail(_email.value, _password.value)
            _authState.value = AuthState.Success
            openAndPopUp(HOME_SCREEN, LOGIN_SCREEN)
        }
    }

    fun onSignInWithGoogle(credential: Credential, openAndPopUp: (String, String) -> Unit) {
        _authState.value = AuthState.Loading

        launchCatching(
            onError = { e ->
                _authState.value = AuthState.Error(e.message ?: "Google login failed")
            }
        ) {
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                accountService.signInWithGoogle(googleIdTokenCredential.idToken)
                _authState.value = AuthState.Success
                openAndPopUp(HOME_SCREEN, LOGIN_SCREEN)
            } else {
                Log.e(ERROR_TAG, UNEXPECTED_CREDENTIAL)
                _authState.value = AuthState.Error("Unexpected credential type")
            }
        }
    }
}