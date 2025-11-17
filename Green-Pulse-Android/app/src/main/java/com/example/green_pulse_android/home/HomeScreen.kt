package com.example.green_pulse_android.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.green_pulse_android.helpers.DisplayNameCard
import com.example.green_pulse_android.helpers.ExitAppCard
import com.example.green_pulse_android.helpers.RemoveAccountCard
import com.example.green_pulse_android.model.User
import com.example.green_pulse_android.ui.theme.GreenPulseAndroidTheme

@Composable
fun HomeScreen(
    restartApp: (String) -> Unit,
    openScreen: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    //LaunchedEffect(Unit) { viewModel.initialize(restartApp) }

    //val userGameData by viewModel.userGameData.collectAsState()
    val error by viewModel.error.collectAsState()
    val user by viewModel.user.collectAsState(initial = User())

        // Main centered content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Green \n\nPulse",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "Welcome to Greenpulse",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Justify,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 32.dp)
            )


            if (error != null) {
                Text(
                    text = "Error: $error",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            DisplayNameCard(user.displayName) { viewModel.onUpdateDisplayNameClick(it) }
            ExitAppCard { viewModel.onSignOutClick(restartApp) }
            RemoveAccountCard { viewModel.onDeleteAccountClick(restartApp) }
        }
    }

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    GreenPulseAndroidTheme() {
        HomeScreen(
            restartApp = {},
            openScreen = {}
        )
    }
}