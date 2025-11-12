package com.example.green_pulse_android.plants

import androidx.compose.foundation.background
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

@Composable
fun PlantViewScreen(
    openScreen: (String) -> Unit
) {
    val viewModel: PlantViewModel = hiltViewModel()  // Use Hilt for repo injection

    val plantsByEnvironment = viewModel.plantsByEnvironment
    val environments = listOf("Indoors", "Outdoors", "Greenhouse")  // Match Firestore environment field

    // Color mapping for environments
    val envColors = mapOf(
        "Indoors" to Color(0xFFE3F2FD),      // Light blue
        "Outdoors" to Color(0xFFFFF3E0),     // Light orange
        "Greenhouse" to Color(0xFFE8F5E8)    // Light green
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        environments.forEach { env ->
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = env.uppercase(),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        IconButton(onClick = { openScreen(CREATE_PLANT_SCREEN) }) {  // Navigate to create
                            Icon(Icons.Default.Add, contentDescription = "Add Plant")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        items(plantsByEnvironment[env] ?: emptyList()) { plant ->
                            val plantColor = envColors[env] ?: MaterialTheme.colorScheme.surface
                            Card(
                                modifier = Modifier
                                    .widthIn(max = 250.dp)
                                    .background(plantColor),
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
                                            Text("Hum:", style = MaterialTheme.typography.bodySmall)
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
                                            Text("pH:", style = MaterialTheme.typography.bodySmall)
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
                                            Text("Temp:", style = MaterialTheme.typography.bodySmall)
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
        // At end of Column
        ExtendedFloatingActionButton(
            onClick = { openScreen(CREATE_PLANT_SCREEN) },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Plant")
            Spacer(modifier = Modifier.size(4.dp))
            Text("New Plant")
        }
    }
}
