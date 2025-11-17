package com.example.green_pulse_android.plants

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlantHistoryDashboardScreen(
    onBack: () -> Unit,
    environment: String = "",  // Nav arg
    plantId: String = "",  // Nav arg
    viewModel: PlantHistoryViewModel = hiltViewModel()
) {
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val colorScheme = MaterialTheme.colorScheme

    LaunchedEffect(environment, plantId) {
        if (environment.isNotEmpty() && environment != uiState.selectedEnvironment) {
            viewModel.selectEnvironment(environment)
            coroutineScope.launch { viewModel.loadPlantsForEnv(environment) }
        }
        if (plantId.isNotEmpty() && plantId != uiState.selectedPlantId) {
            viewModel.selectPlant(plantId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plant History Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Environment Selection
            ExposedDropdownMenuBox(
                expanded = uiState.expandedEnv,
                onExpandedChange = { viewModel.toggleEnvDropdown() }
            ) {
                OutlinedTextField(
                    value = uiState.selectedEnvironment,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Environment") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = uiState.expandedEnv) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = uiState.expandedEnv,
                    onDismissRequest = { viewModel.toggleEnvDropdown() }
                ) {
                    uiState.environments.forEach { env ->
                        DropdownMenuItem(
                            text = { Text(env) },
                            onClick = {
                                viewModel.selectEnvironment(env)
                                coroutineScope.launch { viewModel.loadPlantsForEnv(env) }
                            }
                        )
                    }
                }
            }

            // Plant Selection (visible after env selected)
            if (uiState.selectedEnvironment.isNotEmpty()) {
                ExposedDropdownMenuBox(
                    expanded = uiState.expandedPlant,
                    onExpandedChange = { viewModel.togglePlantDropdown() }
                ) {
                    OutlinedTextField(
                        value = uiState.selectedPlantName,
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Plant") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = uiState.expandedPlant) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = uiState.expandedPlant,
                        onDismissRequest = { viewModel.togglePlantDropdown() }
                    ) {
                        uiState.plantsInEnv.forEach { plant ->
                            DropdownMenuItem(
                                text = { Text(plant.name) },
                                onClick = {
                                    viewModel.selectPlant(plant.id)
                                }
                            )
                        }
                    }
                }
            }

            // Timeframe Selection (buttons)
            if (uiState.selectedPlantId.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("6h", "1d", "1week").forEach { timeframe ->
                        Button(
                            onClick = { viewModel.selectTimeframe(timeframe) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.selectedTimeframe == timeframe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(timeframe)
                        }
                    }
                }
            }

            // Loading/Graph Section
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.history.isNotEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "History for ${uiState.selectedPlantName}",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(16.dp)
                        )
                        AndroidView(
                            factory = { context ->
                                LineChart(context).apply {
                                    description.isEnabled = false
                                    setTouchEnabled(true)
                                    isDragEnabled = true
                                    setScaleEnabled(true)
                                    setPinchZoom(true)
                                    legend.isEnabled = true  // Show legend for multiple lines
                                }
                            },
                            update = { chart ->
                                // Humidity dataset (primary Y: 0-100)
                                val humidityEntries = uiState.history.mapIndexed { index, history ->
                                    Entry(index.toFloat(), history.humidity)
                                }
                                val humidityDataSet = LineDataSet(humidityEntries, "Humidity (%)").apply {
                                    color = colorScheme.primary.toArgb()
                                    setDrawCircles(true)
                                    lineWidth = 2f
                                    setDrawValues(false)
                                    mode = LineDataSet.Mode.CUBIC_BEZIER
                                    axisDependency = YAxis.AxisDependency.LEFT  // Left Y-axis
                                }

                                // pH dataset (secondary Y: 0-14, right axis)
                                val phEntries = uiState.history.mapIndexed { index, history ->
                                    Entry(index.toFloat(), history.ph)
                                }
                                val phDataSet = LineDataSet(phEntries, "pH").apply {
                                    color = colorScheme.secondary.toArgb()
                                    setDrawCircles(true)
                                    lineWidth = 2f
                                    setDrawValues(false)
                                    mode = LineDataSet.Mode.CUBIC_BEZIER
                                    axisDependency = YAxis.AxisDependency.RIGHT  // Right Y-axis
                                }

                                // Temperature dataset (left Y, but normalized or separate scale if needed)
                                val tempEntries = uiState.history.mapIndexed { index, history ->
                                    Entry(index.toFloat(), history.temperature)
                                }
                                val tempDataSet = LineDataSet(tempEntries, "Temperature (°C)").apply {
                                    color = colorScheme.tertiary.toArgb()
                                    setDrawCircles(true)
                                    lineWidth = 2f
                                    setDrawValues(false)
                                    mode = LineDataSet.Mode.CUBIC_BEZIER
                                    axisDependency = YAxis.AxisDependency.LEFT  // Left Y-axis (shared with humidity)
                                }

                                val lineData = LineData(humidityDataSet, phDataSet, tempDataSet)
                                chart.data = lineData

                                // X-axis labels (time)
                                chart.xAxis.valueFormatter = IndexAxisValueFormatter(
                                    uiState.history.takeLast(7).map { history ->
                                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(history.timestamp.toDate())
                                    }
                                )
                                chart.xAxis.position = XAxis.XAxisPosition.BOTTOM

                                // Y-axes setup
                                chart.axisLeft.axisMinimum = 0f
                                chart.axisLeft.axisMaximum = 100f  // For humidity/temp
                                chart.axisRight.axisMinimum = 0f
                                chart.axisRight.axisMaximum = 14f  // For pH
                                chart.axisRight.isEnabled = true  // Enable right axis for pH

                                chart.notifyDataSetChanged()
                                chart.invalidate()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                        )
                    }
                }
                else -> {
                    Text(
                        text = "No history data available. Select a plant and timeframe.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
}