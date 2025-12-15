package com.example.green_pulse_android.plants

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
    environment: String = "",
    plantId: String = "",
    viewModel: PlantHistoryViewModel = hiltViewModel()
) {
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val colorScheme = MaterialTheme.colorScheme

    val availableTimeframes by viewModel.availableTimeframes.collectAsState()
    var selectedMetric by remember { mutableStateOf("Humidity") }
    val metrics = listOf("Humidity", "pH", "Temperature")

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
            if (uiState.selectedPlantId.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    availableTimeframes.forEach { timeframe ->
                        Button(
                            onClick = { viewModel.selectTimeframe(timeframe) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.selectedTimeframe == timeframe)
                                    colorScheme.primary else colorScheme.surfaceVariant
                            )
                        ) {
                            Text(timeframe)
                        }
                    }
                }

                Text("Select metric:", style = MaterialTheme.typography.titleMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    metrics.forEach { metric ->
                        Button(
                            onClick = { selectedMetric = metric },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedMetric == metric)
                                    colorScheme.primary else colorScheme.surfaceVariant
                            )
                        ) { Text(metric) }
                    }
                }
            }

            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                uiState.history.isNotEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "$selectedMetric History for ${uiState.selectedPlantName}",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        AndroidView(
                            factory = { context ->
                                LineChart(context).apply {
                                    description.isEnabled = false
                                    setTouchEnabled(true)
                                    isDragEnabled = true
                                    setScaleEnabled(true)
                                    setPinchZoom(true)
                                    legend.isEnabled = false
                                    xAxis.position = XAxis.XAxisPosition.BOTTOM
                                    xAxis.granularity = 1f
                                    axisRight.isEnabled = false
                                    axisLeft.axisMinimum = 0f
                                }
                            },
                            update = { chart ->
                                val entries = uiState.history.mapIndexed { index, history ->
                                    val value = when (selectedMetric) {
                                        "Humidity" -> history.humidity
                                        "pH" -> history.ph
                                        "Temperature" -> history.temperature
                                        else -> 0f
                                    }
                                    Entry(index.toFloat(), value)
                                }

                                val dataSet = LineDataSet(entries, selectedMetric).apply {
                                    color = when (selectedMetric) {
                                        "Humidity" -> colorScheme.primary.toArgb()
                                        "pH" -> colorScheme.secondary.toArgb()
                                        "Temperature" -> colorScheme.tertiary.toArgb()
                                        else -> colorScheme.primary.toArgb()
                                    }
                                    setCircleColor(color)
                                    setDrawCircleHole(false)
                                    lineWidth = 3.5f
                                    circleRadius = 5f
                                    setDrawValues(false)
                                    mode = LineDataSet.Mode.CUBIC_BEZIER
                                    fillDrawable =
                                        android.graphics.drawable.GradientDrawable().apply {
                                            setColor(0x20FFFFFF)
                                        }
                                    setDrawFilled(true)
                                    axisDependency = YAxis.AxisDependency.LEFT
                                }

                                chart.data = LineData(dataSet)

                                val labelCount = when (uiState.history.size) {
                                    in 0..50 -> uiState.history.size
                                    in 51..200 -> uiState.history.size / 10
                                    else -> uiState.history.size / 20
                                }

                                val labels = uiState.history.reversed().mapIndexedNotNull { index, history ->
                                    if (index % (uiState.history.size / labelCount.coerceAtLeast(1)) == 0 || index == uiState.history.size - 1) {
                                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(history.timestamp.toDate())
                                    } else null
                                }.filterNotNull()
                                chart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
                                chart.xAxis.labelCount = labels.size
                                chart.xAxis.granularity = 1f

                                chart.axisLeft.apply {
                                    axisMinimum = when (selectedMetric) {
                                        "Humidity" -> 0f
                                        "pH" -> 4f
                                        "Temperature" -> 10f
                                        else -> 0f
                                    }
                                    axisMaximum = when (selectedMetric) {
                                        "Humidity" -> 100f
                                        "pH" -> 9f
                                        "Temperature" -> 40f
                                        else -> 100f
                                    }
                                }
                                chart.xAxis.labelCount = 8
                                chart.xAxis.granularity = 1f

                                chart.notifyDataSetChanged()
                                chart.invalidate()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(400.dp)
                                .padding(8.dp)
                        )
                    }
                }
                else -> {
                    Text(
                        text = "No history data available.\nSelect a plant and wait for updates.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
}