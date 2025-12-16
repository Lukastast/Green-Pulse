package com.example.green_pulse_android.plants

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.green_pulse_android.firebase.PlantRepository
import com.example.green_pulse_android.model.FirestorePlant
import com.example.green_pulse_android.model.PlantHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.collections.emptyList

@HiltViewModel
class PlantHistoryViewModel @Inject constructor(
    private val plantRepository: PlantRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlantHistoryUiState())
    val uiState = _uiState.asStateFlow()
    private val _availableTimeframes = MutableStateFlow(listOf("1h", "6h", "All time"))
    val availableTimeframes = _availableTimeframes.asStateFlow()

    data class PlantHistoryUiState(
        val environments: List<String> = listOf("Indoors", "Outdoors", "Greenhouse"),
        val selectedEnvironment: String = "",
        val expandedEnv: Boolean = false,
        val plantsInEnv: List<FirestorePlant> = emptyList(),
        val expandedPlant: Boolean = false,
        val selectedPlantId: String = "",
        val selectedPlantName: String = "",
        val selectedTimeframe: String = "1d",
        val history: List<PlantHistory> = emptyList(),
        val isLoading: Boolean = false
    )

    fun toggleEnvDropdown() {
        _uiState.update { it.copy(expandedEnv = !it.expandedEnv) }
    }

    fun selectEnvironment(env: String) {
        _uiState.update { it.copy(selectedEnvironment = env, expandedEnv = false, plantsInEnv = emptyList()) }
        loadPlantsForEnv(env)
    }

    fun loadPlantsForEnv(env: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val plants = plantRepository.getPlantsForEnv(env)
            _uiState.update {
                it.copy(
                    plantsInEnv = plants,
                    isLoading = false,
                    selectedPlantId = "",
                    selectedPlantName = ""
                )
            }
        }
    }

    fun togglePlantDropdown() {
        _uiState.update { it.copy(expandedPlant = !it.expandedPlant) }
    }

    fun selectPlant(plantId: String) {
        _uiState.update {
            it.copy(
                selectedPlantId = plantId,
                expandedPlant = false
            )
        }
        loadPlantById(plantId, uiState.value.selectedEnvironment)
        loadHistory(plantId, uiState.value.selectedEnvironment)
    }

    private fun getLimitForTimeframe(): Int? = when (uiState.value.selectedTimeframe) {
        "1h" -> 4
        "6h" -> 24
        "All time" -> null
        else -> 240
    }
    fun selectTimeframe(timeframe: String) {
        _uiState.update { it.copy(selectedTimeframe = timeframe) }
        val plantId = uiState.value.selectedPlantId
        val env = uiState.value.selectedEnvironment
        if (plantId.isNotEmpty() && env.isNotEmpty()) {
            loadHistory(plantId, env)
        }
    }

    private fun loadHistory(plantId: String, environment: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val limit = getLimitForTimeframe()
            val result = plantRepository.getPlantHistory(plantId, environment, limit = limit)

            result.fold(
                onSuccess = { fullHistory ->
                    val displayedHistory = if (limit == null) {
                        downsampleHistory(fullHistory)
                    } else {
                        fullHistory.reversed()
                    }

                    _uiState.update {
                        it.copy(
                            history = displayedHistory,
                            isLoading = false
                        )
                    }
                    Log.d("PlantHistoryVM", "Timeframe: ${uiState.value.selectedTimeframe} → Showing ${displayedHistory.size} points")
                },
                onFailure = { e ->
                    Log.e("PlantHistoryVM", "Failed to load history", e)
                    _uiState.update { it.copy(history = emptyList(), isLoading = false) }
                }
            )
        }
    }

    private fun downsampleHistory(fullHistory: List<PlantHistory>): List<PlantHistory> {
        if (fullHistory.isEmpty()) return emptyList()

        val maxPoints = 200

        if (fullHistory.size <= maxPoints) {
            return fullHistory.reversed()
        }

        val downsampled = mutableListOf(fullHistory.first())

        val step = (fullHistory.size - 2) / (maxPoints - 2).coerceAtLeast(1)

        var index = 1
        while (index < fullHistory.size - 1) {
            downsampled.add(fullHistory[index])
            index += step
        }

        if (downsampled.last() != fullHistory.last()) {
            downsampled.add(fullHistory.last())
        }

        return downsampled
    }

    private fun loadPlantById(plantId: String, environment: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val plantResult = plantRepository.getPlantById(environment, plantId)
            plantResult.fold(
                onSuccess = { plant ->
                    _uiState.update {
                        it.copy(
                            selectedPlantName = plant.name ?: "Unknown Plant",
                            selectedPlantId = plant.id,
                            isLoading = false
                        )
                    }
                    Log.d("PlantHistoryVM", "Loaded plant name: ${plant.name} for id $plantId")
                },
                onFailure = { e ->
                    Log.e("PlantHistoryVM", "Failed to load plant by ID", e)
                    _uiState.update { it.copy(selectedPlantName = "Unknown Plant", isLoading = false) }
                }
            )
        }
    }
}