package com.example.green_pulse_android.firebase

import android.util.Log
import com.example.green_pulse_android.model.FirestorePlant
import com.example.green_pulse_android.model.PlantHistory
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlantRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val environmentsCollection = firestore.collection("environments")

    // Create plant in env subcollection
    suspend fun createPlant(plant: FirestorePlant, environment: String): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val userId = auth.currentUser?.uid ?: throw IllegalStateException("User not authenticated")
            val plantToSave = plant.copy(userId = userId, createdAt = Timestamp.now())
            val envDoc = environmentsCollection.document(environment)
            val docRef = envDoc.collection("plants").document(plant.id)
                docRef.set(plantToSave).await()

            // Initial history entry
            val initialHistory = PlantHistory(
                alive = true,
                humidity = plantToSave.humidity,
                ph = plantToSave.ph,
                temperature = plantToSave.temperature
            )
            docRef.collection("history").add(initialHistory).await()
            Log.d("CreatePlantDebug", "Saved plant with custom ID ${plant.id} under $environment")
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e("PlantRepository", "Create plant failed", e)
            Result.failure(e)
        }
    }

    // Listen to all plants across environments (flattens subcollections)
    fun listenToPlants(onUpdate: (List<FirestorePlant>) -> Unit) {
        val userId = auth.currentUser?.uid ?: run {
            Log.w("PlantRepository", "No user logged in; skipping listen")
            return
        }
        val envs = listOf("Indoors", "Outdoors", "Greenhouse")
        var currentPlants = mutableListOf<FirestorePlant>()  // Shared accumulator

        envs.forEach { env ->
            environmentsCollection.document(env)
                .collection("plants")
                .whereEqualTo("userId", userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("PlantRepository", "Listen failed for $env", error)
                        return@addSnapshotListener
                    }
                    val envPlants = snapshot?.toObjects(FirestorePlant::class.java) ?: emptyList()
                    // Remove old plants for this env only (avoids duplicates/clears)
                    currentPlants.removeAll { it.environment == env }
                    // Add updated plants for this env
                    currentPlants.addAll(envPlants)
                    // Trigger update with full merged list
                    Log.d("PlantRepository", "Merged plants from $env: total ${currentPlants.size}")
                    onUpdate(currentPlants.toList())
                }
        }
    }

    // Update plant stats and add to history
    suspend fun updatePlantStats(plantId: String, environment: String, humidity: Float, ph: Float, temp: Float, alive: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val envDoc = environmentsCollection.document(environment)
            val plantDoc = envDoc.collection("plants").document(plantId)
            plantDoc.update(
                "humidity", humidity,
                "ph", ph,
                "temperature", temp,
                "alive", alive
            ).await()

            // Append to history subcollection
            val historyEntry = PlantHistory(alive = alive, humidity = humidity, ph = ph, temperature = temp)
            plantDoc.collection("history").add(historyEntry).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("PlantRepository", "Update stats failed", e)
            Result.failure(e)
        }
    }

    suspend fun getPlantsForEnv(env: String): List<FirestorePlant> = withContext(Dispatchers.IO) {
        try {
            val userId = auth.currentUser?.uid ?: return@withContext emptyList()
            val snapshot = environmentsCollection.document(env)
                .collection("plants")
                .whereEqualTo("userId", userId)
                .get().await()
            snapshot.toObjects(FirestorePlant::class.java)
        } catch (e: Exception) {
            Log.e("PlantRepository", "Get plants for env failed", e)
            emptyList()
        }
    }

    suspend fun getPlantById(environment: String, plantId: String): Result<FirestorePlant> = withContext(Dispatchers.IO) {
        return@withContext try {
            val userId = auth.currentUser?.uid ?: return@withContext Result.failure(IllegalStateException("User not authenticated"))
            Log.d("PlantByIdDebug", "Querying /environments/$environment/plants/$plantId for user $userId")

            val snapshot = environmentsCollection.document(environment)
                .collection("plants")
                .document(plantId)
                .get()
                .await()

            Log.d("PlantByIdDebug", "Doc exists: ${snapshot.exists()}")
            if (snapshot.exists()) {
                val docUserId = snapshot.getString("userId")
                Log.d("PlantByIdDebug", "Doc userId: '$docUserId', Current userId: '$userId'")
            }

            if (snapshot.exists() && snapshot.getString("userId") == userId) {
                val plant = snapshot.toObject(FirestorePlant::class.java) ?: throw IllegalStateException("Plant not found")
                Result.success(plant)
            } else {
                Result.failure(IllegalArgumentException("Plant not owned by user or not found"))
            }
        } catch (e: Exception) {
            Log.e("PlantByIdDebug", "Query failed", e)
            Result.failure(e)
        }
    }

    suspend fun getPlantHistory(plantId: String, environment: String, limit: Int = 50): Result<List<PlantHistory>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val envDoc = environmentsCollection.document(environment)
            val historyQuery = envDoc.collection("plants").document(plantId)
                .collection("history")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit.toLong())

            val snapshots = historyQuery.get().await()
            val history = try {
                snapshots.toObjects(PlantHistory::class.java)
            } catch (e: RuntimeException) {
                Log.e("PlantRepository", "Deserialization failed for history", e)
                emptyList()  // Fallback—don't crash the app
            }
            Result.success(history)
        } catch (e: Exception) {
            Log.e("PlantRepository", "Get history failed", e)
            Result.failure(e)
        }
    }
}