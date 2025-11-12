package com.example.green_pulse_android.firebase

import android.util.Log
import com.example.green_pulse_android.model.FirestorePlant
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlantRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth  // Injected for userId
) {
    private val plantsCollection = firestore.collection("plants")

    suspend fun createPlant(plant: FirestorePlant): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val userId = auth.currentUser?.uid ?: throw IllegalStateException("User not authenticated")
            val plantToSave = plant.copy(
                userId = userId,
                createdAt = Timestamp.now()
            )
            val docRef = plantsCollection.add(plantToSave).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e("PlantRepository", "Create plant failed", e)
            Result.failure(e)
        }
    }

    fun listenToPlants(onUpdate: (List<FirestorePlant>) -> Unit) {
        val userId = auth.currentUser?.uid ?: run {
            Log.w("PlantRepository", "No user logged in; skipping listen")
            return
        }
        plantsCollection
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("PlantRepository", "Listen failed", error)
                    return@addSnapshotListener
                }
                val plants = snapshot?.toObjects(FirestorePlant::class.java) ?: emptyList()
                onUpdate(plants)
            }
    }

    suspend fun updatePlantStats(plantId: String, humidity: Float, ph: Float, temp: Float, alive: Boolean) = withContext(Dispatchers.IO) {
        return@withContext try {
            plantsCollection.document(plantId).update(
                "humidity", humidity,
                "ph", ph,
                "temperature", temp,
                "alive", alive
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("PlantRepository", "Update stats failed", e)
            Result.failure(e)
        }
    }
}