package com.example.green_pulse_android.createplant

import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.green_pulse_android.firebase.PlantRepository
import com.example.green_pulse_android.model.FirestorePlant
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject

@HiltViewModel
class CreatePlantViewModel @Inject constructor(
    private val plantRepository: PlantRepository
) : ViewModel() {
    suspend fun createPlant(plant: FirestorePlant, onResult: (FirestorePlant?) -> Unit) {
        plantRepository.createPlant(plant).fold(
            onSuccess = { docId ->
                val savedPlant = plant.copy(id = docId)  // Update with Firestore ID
                onResult(savedPlant)
            },
            onFailure = { e ->
                Log.e("CreatePlantVM", "Failed to create", e)
                onResult(null)
            }
        )
    }
}