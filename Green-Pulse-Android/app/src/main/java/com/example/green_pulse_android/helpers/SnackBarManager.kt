package com.example.green_pulse_android.helpers

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object SnackbarManager {
    private val messages: MutableStateFlow<String?> = MutableStateFlow(null)
    val snackbarMessages: StateFlow<String?>
        get() = messages

    fun showMessage(message: String) {
        messages.value = message
    }

    fun clearSnackbarState() {
        messages.value = null
    }
}