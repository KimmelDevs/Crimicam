package com.example.crimicam.facerecognitionnetface.models.data

import com.example.crimicam.data.model.Criminal
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

class CriminalDB {
    private val firestore = FirebaseFirestore.getInstance()
    private val criminalsCollection = firestore.collection("criminals")

    /**
     * Add a new criminal to the database
     * Returns the auto-generated criminal ID
     */
    suspend fun addCriminal(criminal: Criminal): String {
        return withContext(Dispatchers.IO) {
            try {
                val criminalWithTimestamp = criminal.copy(
                    createdAt = criminal.createdAt ?: Timestamp.now(),
                    lastUpdated = Timestamp.now()
                )

                if (criminal.id.isEmpty()) {
                    // Auto-generate ID
                    val docRef = criminalsCollection.add(criminalWithTimestamp).await()
                    docRef.id
                } else {
                    criminalsCollection.document(criminal.id).set(criminalWithTimestamp).await()
                    criminal.id
                }
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    /**
     * Update an existing criminal's data
     */
    suspend fun updateCriminal(criminalId: String, updates: Map<String, Any>) {
        withContext(Dispatchers.IO) {
            try {
                val updatesWithTimestamp = updates.toMutableMap()
                updatesWithTimestamp["lastUpdated"] = Timestamp.now()

                criminalsCollection.document(criminalId)
                    .update(updatesWithTimestamp)
                    .await()
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    /**
     * Increment the image count for a criminal atomically
     */
    suspend fun incrementImageCount(criminalId: String) {
        withContext(Dispatchers.IO) {
            try {
                criminalsCollection.document(criminalId).update(
                    mapOf(
                        "imageCount" to FieldValue.increment(1),
                        "lastUpdated" to Timestamp.now()
                    )
                ).await()
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    /**
     * Decrement the image count for a criminal atomically
     */
    suspend fun decrementImageCount(criminalId: String) {
        withContext(Dispatchers.IO) {
            try {
                criminalsCollection.document(criminalId).update(
                    mapOf(
                        "imageCount" to FieldValue.increment(-1),
                        "lastUpdated" to Timestamp.now()
                    )
                ).await()
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    /**
     * Get a single criminal by ID
     */
    suspend fun getCriminal(criminalId: String): Criminal? {
        return withContext(Dispatchers.IO) {
            try {
                val document = criminalsCollection.document(criminalId).get().await()
                document.toObject(Criminal::class.java)?.copy(
                    id = document.id
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Get all criminals as a Flow (reactive)
     */
    fun getAll(): Flow<List<Criminal>> = callbackFlow {
        val listenerRegistration = criminalsCollection
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val criminals = snapshot.documents.mapNotNull { document ->
                        document.toObject(Criminal::class.java)?.copy(
                            id = document.id
                        )
                    }
                    trySend(criminals)
                }
            }

        awaitClose {
            listenerRegistration.remove()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get all criminals as a list (one-time fetch)
     */
    suspend fun getAllOnce(): List<Criminal> {
        return withContext(Dispatchers.IO) {
            try {
                val snapshot = criminalsCollection.get().await()
                snapshot.documents.mapNotNull { document ->
                    document.toObject(Criminal::class.java)?.copy(
                        id = document.id
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    /**
     * Get the total count of criminals in the database
     */
    suspend fun getCount(): Long {
        return withContext(Dispatchers.IO) {
            try {
                criminalsCollection.get().await().size().toLong()
            } catch (e: Exception) {
                e.printStackTrace()
                0L
            }
        }
    }

    /**
     * Remove a criminal from the database
     */
    suspend fun removeCriminal(criminalId: String) {
        withContext(Dispatchers.IO) {
            try {
                criminalsCollection.document(criminalId).delete().await()
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    /**
     * Search for criminals by name
     */
    suspend fun searchByName(firstName: String, lastName: String): List<Criminal> {
        return withContext(Dispatchers.IO) {
            try {
                val snapshot = criminalsCollection
                    .whereEqualTo("firstName", firstName)
                    .whereEqualTo("lastName", lastName)
                    .get()
                    .await()

                snapshot.documents.mapNotNull { document ->
                    document.toObject(Criminal::class.java)?.copy(
                        id = document.id
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    /**
     * Search criminals by status
     */
    suspend fun searchByStatus(status: String): List<Criminal> {
        return withContext(Dispatchers.IO) {
            try {
                val snapshot = criminalsCollection
                    .whereEqualTo("status", status)
                    .get()
                    .await()

                snapshot.documents.mapNotNull { document ->
                    document.toObject(Criminal::class.java)?.copy(
                        id = document.id
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    /**
     * Search criminals by risk level
     */
    suspend fun searchByRiskLevel(riskLevel: String): List<Criminal> {
        return withContext(Dispatchers.IO) {
            try {
                val snapshot = criminalsCollection
                    .whereEqualTo("riskLevel", riskLevel)
                    .get()
                    .await()

                snapshot.documents.mapNotNull { document ->
                    document.toObject(Criminal::class.java)?.copy(
                        id = document.id
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    /**
     * Check if a criminal with the given name exists
     */
    suspend fun existsByName(firstName: String, lastName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val snapshot = criminalsCollection
                    .whereEqualTo("firstName", firstName)
                    .whereEqualTo("lastName", lastName)
                    .limit(1)
                    .get()
                    .await()

                !snapshot.isEmpty
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * Clear all criminals from the database
     */
    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            try {
                val snapshot = criminalsCollection.get().await()

                // Delete in batches of 500 (Firestore limit)
                val batches = snapshot.documents.chunked(500)

                batches.forEach { batch ->
                    val deleteBatch = firestore.batch()
                    batch.forEach { document ->
                        deleteBatch.delete(document.reference)
                    }
                    deleteBatch.commit().await()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }
}