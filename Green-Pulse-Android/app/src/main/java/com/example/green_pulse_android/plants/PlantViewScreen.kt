package com.example.green_pulse_android.plants

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.green_pulse_android.helpers.CREATE_PLANT_SCREEN
import com.example.green_pulse_android.helpers.PLANT_DASHBOARD_SCREEN  // Add this import

val envColors = mapOf(
    "Indoors" to Color(0xFFE3F2FD),      // Light blue
    "Outdoors" to Color(0xFFFFF3E0),     // Light orange
    "Greenhouse" to Color(0xFFE8F5E8)    // Light green
)

@Composable
fun PlantViewScreen(
    openScreen: (String) -> Unit
) {
    val viewModel: PlantViewModel = hiltViewModel()
    val plantsByEnvironment = viewModel.plantsByEnvironment
    val isLoading by viewModel.isLoading
    val environments = listOf("Indoors", "Outdoors", "Greenhouse")

    LaunchedEffect(Unit) {
        viewModel.refreshPlants()  // New method below
    }

    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            environments.forEach { env ->
                val envPlants = plantsByEnvironment[env] ?: emptyList()
                Card(modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = env.uppercase(),
                                style = MaterialTheme.typography.headlineSmall
                            )
                            IconButton(onClick = {
                                openScreen("$CREATE_PLANT_SCREEN/$env")
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "Add Plant")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (envPlants.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No plants in $env yet. Add one!")
                            }
                        } else {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                items(plantsByEnvironment[env] ?: emptyList()) { plant ->
                                    val plantColor =
                                        envColors[env] ?: MaterialTheme.colorScheme.surface
                                    Card(
                                        modifier = Modifier
                                            .widthIn(max = 250.dp)
                                            .background(plantColor)
                                            .clickable {
                                                Log.d("PlantView", "Tapping plant ${plant.name} in $env, id=${plant.id}")
                                                openScreen("$PLANT_DASHBOARD_SCREEN/$env/${plant.id}")
                                            },
                                        colors = CardDefaults.cardColors(containerColor = plantColor)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(8.dp)
                                        ) {
                                            Text(
                                                text = if (!plant.alive) "${plant.name} 💀 (DEAD)" else plant.name,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                modifier = Modifier.align(Alignment.CenterHorizontally)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            if (plant.alive) {
                                                // Humidity Bar
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        "Hum:",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                    Spacer(modifier = Modifier.size(4.dp))
                                                    LinearProgressIndicator(
                                                        progress = { plant.humidity / 100f },
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(8.dp)
                                                    )
                                                    Spacer(modifier = Modifier.size(4.dp))
                                                    Text(
                                                        "${plant.humidity.toInt()}%",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                // pH Bar
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        "pH:",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                    Spacer(modifier = Modifier.size(4.dp))
                                                    LinearProgressIndicator(
                                                        progress = { plant.ph / 14f },
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(8.dp)
                                                    )
                                                    Spacer(modifier = Modifier.size(4.dp))
                                                    Text(
                                                        "${plant.ph}",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                // Temperature Bar
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        "Temp:",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                    Spacer(modifier = Modifier.size(4.dp))
                                                    LinearProgressIndicator(
                                                        progress = { plant.temperature / 50f },
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(8.dp)
                                                    )
                                                    Spacer(modifier = Modifier.size(4.dp))
                                                    Text(
                                                        "${plant.temperature.toInt()}°C",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            }
        }
    }
}