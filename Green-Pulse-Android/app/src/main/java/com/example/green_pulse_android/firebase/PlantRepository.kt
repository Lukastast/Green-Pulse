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
    private fun getUserPath(): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User not authenticated")
        return "users/$uid"
    }
    suspend fun createPlant(plant: FirestorePlant, environment: String): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val uid = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")
            val userRoot = firestore.collection("users").document(uid)

            val plantRef = userRoot
                .collection("environments")
                .document(environment)
                .collection("plants")
                .document(plant.id)

            val plantToSave = plant.copy(createdAt = Timestamp.now())
            plantRef.set(plantToSave).await()
            // Initial history
            val initialHistory = PlantHistory(
                alive = true,
                humidity = plant.humidity,
                ph = plant.ph,
                temperature = plant.temperature
            )
            plantRef.collection("history").add(initialHistory).await()

            Log.d("PlantRepo", "Created plant ${plant.id} in $environment for user $uid")
            Result.success(plant.id)
        } catch (e: Exception) {
            Log.e("PlantRepo", "Create plant failed", e)
            Result.failure(e)
        }
    }
    fun listenToPlants(onUpdate: (List<FirestorePlant>) -> Unit) {
        val uid = auth.currentUser?.uid ?: run {
            Log.w("PlantRepo", "No user logged in; skipping listen")
            return
        }

        val envs = listOf("Indoors", "Outdoors", "Greenhouse")
        val allPlants = mutableListOf<FirestorePlant>()

        envs.forEach { env ->
            val plantsRef = firestore
                .collection("users").document(uid)
                .collection("environments").document(env)
                .collection("plants")

            plantsRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("PlantRepo", "Listen failed for $env", error)
                    return@addSnapshotListener
                }

                val envPlants = snapshot?.toObjects(FirestorePlant::class.java) ?: emptyList()

                // Remove old plants from this env, add new ones
                allPlants.removeAll { it.environment == env }
                allPlants.addAll(envPlants.map { it.copy(environment = env) }) // ensure env field

                Log.d("PlantRepo", "Updated $env → total plants: ${allPlants.size}")
                onUpdate(allPlants.toList())
            }
        }
    }

    suspend fun updatePlantStats(plantId: String, environment: String, humidity: Float, ph: Float, temp: Float, alive: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val userPath = getUserPath()
            val envDoc = firestore.collection(userPath).document("environments").collection(environment)
            val plantDoc = envDoc.document(plantId)
            plantDoc.update(
                "humidity", humidity,
                "ph", ph,
                "temperature", temp,
                "alive", alive
            ).await()

            // Append to history
            val historyEntry = PlantHistory(alive = alive, humidity = humidity, ph = ph, temperature = temp)
            plantDoc.collection("history").add(historyEntry).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("PlantRepository", "Update stats failed", e)
            Result.failure(e)
        }
    }

    suspend fun getPlantById(environment: String, plantId: String): Result<FirestorePlant> = withContext(Dispatchers.IO) {
        return@withContext try {
            val userPath = getUserPath()
            val snapshot = firestore.collection(userPath)
                .document("environments")
                .collection(environment)
                .document(plantId)
                .get()
                .await()

            if (snapshot.exists()) {
                val plant = snapshot.toObject(FirestorePlant::class.java) ?: throw IllegalStateException("Plant not found")
                Result.success(plant)
            } else {
                Result.failure(IllegalArgumentException("Plant not found"))
            }
        } catch (e: Exception) {
            Log.e("PlantRepository", "Get plant by ID failed", e)
            Result.failure(e)
        }
    }

    suspend fun getPlantHistory(plantId: String, environment: String, limit: Int = 50): Result<List<PlantHistory>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val userPath = getUserPath()
            val historyQuery = firestore.collection(userPath)
                .document("environments")
                .collection(environment)
                .document(plantId)
                .collection("history")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit.toLong())

            val snapshots = historyQuery.get().await()
            val history = snapshots.toObjects(PlantHistory::class.java)
            Result.success(history)
        } catch (e: Exception) {
            Log.e("PlantRepository", "Get history failed", e)
            Result.failure(e)
        }
    }

    suspend fun getPlantsForEnv(env: String): List<FirestorePlant> = withContext(Dispatchers.IO) {
        return@withContext try {
            val userPath = getUserPath()  // e.g., "users/myUid"
            val snapshot = firestore  // Start from firestore instance
                .collection("$userPath/environments/$env/plants")  // Direct path string for subcollection
                .get()
                .await()  // Await Task<QuerySnapshot>
            val plants = snapshot.toObjects(FirestorePlant::class.java)  // Now resolves
            Log.d("PlantRepository", "Loaded ${plants.size} plants for env $env")
            plants
        } catch (e: Exception) {
            Log.e("PlantRepository", "Get plants for env failed", e)
            emptyList()
        }
    }
}