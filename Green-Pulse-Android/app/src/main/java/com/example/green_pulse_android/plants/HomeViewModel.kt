package com.example.green_pulse_android.plants

import com.example.green_pulse_android.GreenPulseViewModel
import com.example.green_pulse_android.firebase.AccountService
import com.example.green_pulse_android.helpers.LOGIN_SCREEN
import com.example.green_pulse_android.helpers.SPLASH_SCREEN
import com.example.green_pulse_android.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val accountService: AccountService,
    //private val userRepository: UserRepository  // Inject the repository
) : GreenPulseViewModel() {
    private val _user = MutableStateFlow(User())
    val user: StateFlow<User> = _user.asStateFlow()
   // private val _userGameData = MutableStateFlow<UserGameData?>(null)
   // val userGameData: StateFlow<UserGameData?> = _userGameData.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        launchCatching {
            _user.value = accountService.getUserProfile()
        }
    }

    fun initialize(restartApp: (String) -> Unit) {
            launchCatching {
                accountService.currentUser.collect { user ->
                    if (user == null) restartApp(SPLASH_SCREEN)
                }
            }
        }
   fun onUpdateDisplayNameClick(newDisplayName: String) {
       launchCatching {
           accountService.updateDisplayName(newDisplayName)
           _user.value = accountService.getUserProfile()
       }
   }
    fun onSignOutClick(restartApp: (String) -> Unit) {
        launchCatching {
            accountService.signOut()
            restartApp(LOGIN_SCREEN)
        }
    }

    fun onDeleteAccountClick(restartApp: (String) -> Unit) {
        launchCatching {
            accountService.deleteAccount()
            restartApp(SPLASH_SCREEN)
        }
    }

}