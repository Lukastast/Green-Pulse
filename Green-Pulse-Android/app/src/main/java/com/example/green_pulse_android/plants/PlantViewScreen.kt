package com.example.green_pulse_android.plants

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.green_pulse_android.helpers.CREATE_PLANT_SCREEN
import com.example.green_pulse_android.helpers.PLANT_DASHBOARD_SCREEN
import com.example.green_pulse_android.model.Plant

val envColors = mapOf(
    "Indoors" to Color(0xFFE3F2FD),
    "Outdoors" to Color(0xFFFFF3E0),
    "Greenhouse" to Color(0xFFE8F5E8)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantViewScreen(
    openScreen: (String) -> Unit
) {
    val viewModel: PlantViewModel = hiltViewModel()
    val plantsByEnvironment = viewModel.plantsByEnvironment
    val isLoading by viewModel.isLoading
    val environments = listOf("Indoors", "Outdoors", "Greenhouse")

    // For delete confirmation dialog
    var showDeleteDialog by remember { mutableStateOf(false) }
    var plantToDelete by remember { mutableStateOf<Plant?>(null) }

    LaunchedEffect(Unit) { viewModel.refreshPlants() }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            environments.forEach { env ->
                val envPlants = plantsByEnvironment[env] ?: emptyList()

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = env.uppercase(),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            IconButton(onClick = { openScreen("$CREATE_PLANT_SCREEN/$env") }) {
                                Icon(Icons.Default.Add, contentDescription = "Add Plant", tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (envPlants.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No plants in $env yet. Add one!", color = MaterialTheme.colorScheme.onPrimary)
                            }
                        } else {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp)
                            ) {
                                items(envPlants) { plant ->
                                    val plantColor = envColors[env] ?: MaterialTheme.colorScheme.surface

                                    Card(
                                        modifier = Modifier
                                            .width(280.dp)  // ← FIXED WIDTH (instead of stretching)
                                            .clickable {
                                                openScreen("$PLANT_DASHBOARD_SCREEN/$env/${plant.id}")
                                            },
                                        colors = CardDefaults.cardColors(containerColor = plantColor),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            // Delete button top-right
                                            IconButton(
                                                onClick = {
                                                    plantToDelete = plant
                                                    showDeleteDialog = true
                                                },
                                                modifier = Modifier.align(Alignment.TopEnd)
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Delete",
                                                    tint = Color.Red.copy(alpha = 0.7f)
                                                )
                                            }

                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp)
                                                    .padding(top = 32.dp), // space for delete button
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = if (!plant.alive) "${plant.name} (DEAD)" else plant.name,
                                                    style = MaterialTheme.typography.titleLarge.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.Black
                                                    )
                                                )
                                                Spacer(modifier = Modifier.height(12.dp))

                                                if (plant.alive) {
                                                    StatRow("Hum:", "${plant.humidity.toInt()}%", progress = plant.humidity / 100f)
                                                    StatRow("pH:", plant.ph.toString(), progress = plant.ph / 14f)
                                                    StatRow("Temp:", "${plant.temperature.toInt()}°C", progress = plant.temperature / 50f)
                                                } else {
                                                    Text("Plant has died", color = Color.Red, style = MaterialTheme.typography.bodyLarge)
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

    // Delete Confirmation Dialog
    if (showDeleteDialog && plantToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Plant?") },
            text = { Text("Are you sure you want to delete ${plantToDelete!!.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlant(plantToDelete!!)
                    showDeleteDialog = false
                    plantToDelete = null
                }) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StatRow(label: String, value: String, progress: Float) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.Black.copy(alpha = 0.8f))
        Spacer(modifier = Modifier.width(8.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.LightGray.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Color.Black)
    }
    Spacer(modifier = Modifier.height(8.dp))
}