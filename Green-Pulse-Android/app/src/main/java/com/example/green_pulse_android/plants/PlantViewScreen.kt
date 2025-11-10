package com.example.green_pulse_android.plants

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PlantViewScreen(openScreen: (String) -> Unit,) {
    val context = LocalContext.current
    val factory = remember(context) { PlantViewModelFactory(context) }
    val viewModel: PlantViewModel = viewModel(factory = factory)

    val plantsByType = viewModel.plantsByType
    val plantTypes = listOf("cactus", "tropical", "herb")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        plantTypes.forEach { type ->
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
                            text = type.uppercase(),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Button(onClick = { viewModel.addPlant(type) }) {
                            Text("Add $type")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(plantsByType[type] ?: emptyList()) { plant ->
                            Card(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (!plant.alive) "${plant.id} 💀 (DEAD)" else plant.id,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (plant.alive) {
                                        Button(
                                            onClick = { viewModel.waterPlant(plant.id) },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Text("💧")
                                        }
                                        Button(
                                            onClick = { viewModel.setPhPlant(plant.id) },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Text("⚗️")
                                        }
                                        Button(
                                            onClick = { viewModel.setTempPlant(plant.id) },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Text("🌡️")
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