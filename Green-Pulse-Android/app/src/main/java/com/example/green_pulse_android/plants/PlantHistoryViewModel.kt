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

    init {
        // Default to first env
        _uiState.update { it.copy(selectedEnvironment = "Indoors") }
    }

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
            // Assume repo has getPlantsForEnv(env: String) -> List<FirestorePlant>
            val plants = plantRepository.getPlantsForEnv(env)  // Implement in repo
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
        // Auto-load history for selected plant
        loadHistory(plantId, uiState.value.selectedEnvironment)
    }

    fun selectTimeframe(timeframe: String) {
        _uiState.update { it.copy(selectedTimeframe = timeframe) }
        val plantId = uiState.value.selectedPlantId
        if (plantId.isNotEmpty()) {
            loadHistory(plantId, uiState.value.selectedEnvironment)
        }
    }

    private fun loadHistory(plantId: String, environment: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val historyResult = plantRepository.getPlantHistory(plantId, environment, getLimitForTimeframe())
            historyResult.fold(
                onSuccess = { history ->
                    _uiState.update { it.copy(history = history, isLoading = false) }
                    Log.d("PlantHistoryVM", "Loaded ${history.size} history entries for plant $plantId")
                },
                onFailure = { e ->
                    Log.e("PlantHistoryVM", "Failed to load history", e)
                    _uiState.update { it.copy(history = emptyList(), isLoading = false) }
                }
            )
        }
    }

    private fun loadPlantById(plantId: String, environment: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            // Assume repo has getPlantById(env: String, id: String) -> FirestorePlant?
            val plantResult = plantRepository.getPlantById(environment, plantId)
            plantResult.fold(
                onSuccess = { plant ->
                    _uiState.update {
                        it.copy(
                            selectedPlantName = plant.name ?: "Unknown Plant",
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
    private fun getLimitForTimeframe(): Int = when (uiState.value.selectedTimeframe) {
        "6h" -> 6 * 6  // Assume hourly updates
        "1d" -> 24
        "1week" -> 168
        else -> 24
    }
}