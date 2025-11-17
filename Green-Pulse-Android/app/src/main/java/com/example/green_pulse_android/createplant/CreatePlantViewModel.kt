package com.example.green_pulse_android.createplant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.green_pulse_android.firebase.PlantRepository
import com.example.green_pulse_android.model.FirestorePlant
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CreatePlantViewModel @Inject constructor(
    private val plantRepository: PlantRepository
) : ViewModel() {

    fun createPlant(
        plant: FirestorePlant,
        environment: String,
        onResult: (FirestorePlant?) -> Unit,
        onSuccessRefresh: (() -> Unit)? = null,
        function: () -> Unit
    ) {  // Add refresh callback
        viewModelScope.launch {
            val result = plantRepository.createPlant(plant, environment)
            result.fold(
                onSuccess = { docId ->
                    val savedPlant = plant.copy(id = docId)
                    onResult(savedPlant)
                    onSuccessRefresh?.invoke()  // Trigger refresh in PlantViewModel
                },
                onFailure = { e ->
                    onResult(null)
                }
            )
        }
    }
}