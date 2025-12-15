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
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

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

                allPlants.removeAll { it.environment == env }
                allPlants.addAll(envPlants.map { it.copy(environment = env) })

                Log.d("PlantRepo", "Updated $env → total plants: ${allPlants.size}")
                onUpdate(allPlants.toList())
            }
        }
    }

    suspend fun deletePlant(plantId: String, environment: String): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val uid = auth.currentUser?.uid
                ?: return@withContext Result.failure(IllegalStateException("User not authenticated"))

            val plantRef = firestore
                .collection("users")
                .document(uid)
                .collection("environments")
                .document(environment)
                .collection("plants")
                .document(plantId)

            plantRef.delete().await()

            Log.d("PlantRepo", "Deleted plant $plantId from $environment")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("PlantRepo", "Failed to delete plant $plantId", e)
            Result.failure(e)
        }
    }

    suspend fun updatePlantStats(
        plantId: String,
        environment: String,
        humidity: Float,
        ph: Float,
        temp: Float,
        alive: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val uid = auth.currentUser?.uid ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))

            val plantRef = firestore
                .collection("users").document(uid)
                .collection("environments").document(environment)
                .collection("plants").document(plantId)

            plantRef.update(
                "humidity", humidity,
                "ph", ph,
                "temperature", temp,
                "alive", alive
            ).await()

            val historyEntry = PlantHistory(
                timestamp = Timestamp.now(),
                alive = alive,
                humidity = humidity,
                ph = ph,
                temperature = temp
            )
            plantRef.collection("history").add(historyEntry).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("PlantRepository", "Update stats failed", e)
            Result.failure(e)
        }
    }

    suspend fun addHistoryEntry(
        plantId: String,
        environment: String,
        history: PlantHistory
    ): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val uid = auth.currentUser?.uid ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))

            val historyRef = firestore
                .collection("users").document(uid)
                .collection("environments").document(environment)
                .collection("plants").document(plantId)
                .collection("history")

            historyRef.add(history).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("PlantRepo", "Failed to add history", e)
            Result.failure(e)
        }
    }

    suspend fun getPlantById(environment: String, plantId: String): Result<FirestorePlant> = withContext(Dispatchers.IO) {
        return@withContext try {
            val uid = auth.currentUser?.uid ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))

            val plantDoc = firestore
                .collection("users").document(uid)
                .collection("environments").document(environment)
                .collection("plants").document(plantId)

            val snapshot = plantDoc.get().await()
            if (snapshot.exists()) {
                val plant = snapshot.toObject(FirestorePlant::class.java)
                    ?: return@withContext Result.failure(IllegalStateException("Parse error"))
                Result.success(plant.copy(environment = environment))
            } else {
                Result.failure(IllegalArgumentException("Plant not found"))
            }
        } catch (e: Exception) {
            Log.e("PlantRepo", "getPlantById failed", e)
            Result.failure(e)
        }
    }

    suspend fun getPlantHistory(
        plantId: String,
        environment: String,
        limit: Int? = null
    ): Result<List<PlantHistory>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val uid = auth.currentUser?.uid ?: return@withContext Result.failure(IllegalStateException("Not authenticated"))

            var query = firestore
                .collection("users").document(uid)
                .collection("environments").document(environment)
                .collection("plants").document(plantId)
                .collection("history")
                .orderBy("timestamp", Query.Direction.DESCENDING)

            if (limit != null) {
                query = query.limit(limit.toLong())
            }

            val snapshots = query.get().await()
            val history = snapshots.toObjects(PlantHistory::class.java)
            Result.success(history)
        } catch (e: Exception) {
            Log.e("PlantRepo", "getPlantHistory failed", e)
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

    suspend fun createTestPlantWithHistory(
        environment: String = "Indoors",
        name: String = "Test Plant",
        type: String = "herb"
    ): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val uid = auth.currentUser?.uid ?: return@withContext Result.failure(IllegalStateException("Not logged in"))

            val plantId = UUID.randomUUID().toString()

            val plant = FirestorePlant(
                id = plantId,
                name = name,
                type = type,
                environment = environment,
                humidity = 60f,
                ph = 6.5f,
                temperature = 22f,
                alive = true,
                createdAt = Timestamp.now()
            )

            val plantRef = firestore
                .collection("users").document(uid)
                .collection("environments").document(environment)
                .collection("plants").document(plantId)

            // Create the plant
            plantRef.set(plant).await()

            // Generate test history
            val now = System.currentTimeMillis()
            val entries = mutableListOf<PlantHistory>()

            // 10 minutes of data (40 entries, every 15 seconds)
            repeat(40) { i ->
                val timestamp = Timestamp((now - (i * 15 * 1000)) / 1000, 0)
                entries.add(
                    PlantHistory(
                        timestamp = timestamp,
                        humidity = 50f + Random.nextFloat() * 20f,  // 50-70%
                        ph = 6.0f + Random.nextFloat() * 1.5f,      // 6.0-7.5
                        temperature = 20f + Random.nextFloat() * 8f, // 20-28°C
                        alive = true
                    )
                )
            }

            // Add 6 hours more (up to ~1440 total)
            repeat(1400) { i ->
                val offset = 40 + i
                val timestamp = Timestamp((now - (offset * 15 * 1000)) / 1000, 0)
                entries.add(
                    PlantHistory(
                        timestamp = timestamp,
                        humidity = 40f + Random.nextFloat() * 40f,
                        ph = 5.5f + Random.nextFloat() * 2f,
                        temperature = 18f + Random.nextFloat() * 12f,
                        alive = if (i > 1200) false else true  // Dies near the end
                    )
                )
            }

            // Save all history entries
            entries.forEach { entry ->
                plantRef.collection("history").add(entry).await()
            }

            Log.d("TestPlant", "Created test plant $plantId with ${entries.size} history entries")
            Result.success(plantId)
        } catch (e: Exception) {
            Log.e("TestPlant", "Failed to create test plant", e)
            Result.failure(e)
        }
    }

}