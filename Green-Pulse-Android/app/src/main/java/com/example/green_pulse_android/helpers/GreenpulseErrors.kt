package com.example.green_pulse_android.helpers

import android.util.Log
import com.google.firebase.auth.FirebaseAuthException

const val ERROR_TAG = "PixelFit APP ERROR"
const val UNEXPECTED_CREDENTIAL = "Unexpected type of credential"

object AuthErrorMapper {

    private const val DEFAULT_ERROR = "Operation failed"


    val loginErrorMappings = mapOf(
        "ERROR_INVALID_EMAIL" to "Invalid email",
        "ERROR_WRONG_PASSWORD" to "Incorrect password",
        "ERROR_USER_NOT_FOUND" to "No account found with this email",
        "ERROR_USER_DISABLED" to "Account disabled",
        "ERROR_INVALID_CREDENTIAL" to "Invalid email or password"
    )


    val signupErrorMappings = mapOf(
        "ERROR_INVALID_EMAIL" to "Invalid email format",
        "ERROR_EMAIL_ALREADY_IN_USE" to "Email already in use",
        "ERROR_WEAK_PASSWORD" to "Password is too weak",
        "ERROR_INVALID_CREDENTIAL" to "Invalid credentials"
    )

    fun mapError(
        e: Throwable,
        mappings: Map<String, String>,
        defaultMessage: String = DEFAULT_ERROR,
        errorTag: String = ERROR_TAG
    ): String {
        return if (e is FirebaseAuthException) {
            Log.e(errorTag, "Firebase error code: ${e.errorCode}")
            mappings[e.errorCode] ?: (e.message ?: defaultMessage)
        } else {
            e.message ?: "An error occurred"
        }
    }
}