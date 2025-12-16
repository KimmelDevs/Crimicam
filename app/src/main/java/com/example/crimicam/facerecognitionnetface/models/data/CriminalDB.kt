package com.example.crimicam.facerecognitionnetface.models.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
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

/**
 * Global criminal records database
 * Uses collection: "criminals"
 * Pairs with: CriminalImagesVectorDB (global "criminal_face_images")
 */
@Single
class CriminalDB {
    private val firestore = FirebaseFirestore.getInstance()

    // Global collection - NOT a subcollection
    private fun getCriminalsCollection(): CollectionReference {
        return firestore.collection("criminals")
    }

    /**
     * Add a new criminal record
     * @return The auto-generated criminal ID
     */
    suspend fun addCriminalRecord(criminal: CriminalRecord): String {
        return withContext(Dispatchers.IO) {
            try {
                val criminalData = criminal.copy(
                    createdAt = Timestamp.now(),
                    lastUpdated = Timestamp.now()
                )

                if (criminal.criminalID.isEmpty()) {
                    // Auto-generate ID
                    val docRef = getCriminalsCollection().add(criminalData).await()
                    docRef.id
                } else {
                    // Use provided ID
                    getCriminalsCollection().document(criminal.criminalID).set(criminalData).await()
                    criminal.criminalID
                }
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    /**
     * Get a single criminal by ID
     */
    suspend fun getCriminalRecord(criminalId: String): CriminalRecord? {
        return withContext(Dispatchers.IO) {
            try {
                getCriminalsCollection()
                    .document(criminalId)
                    .get()
                    .await()
                    .toObject(CriminalRecord::class.java)?.copy(criminalID = criminalId)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Get all criminals as a reactive Flow
     */
    fun getAll(): Flow<List<CriminalRecord>> = callbackFlow {
        val listenerRegistration = getCriminalsCollection().addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val criminals = snapshot.documents.mapNotNull { document ->
                    document.toObject(CriminalRecord::class.java)?.copy(criminalID = document.id)
                }
                trySend(criminals)
            }
        }

        awaitClose {
            listenerRegistration.remove()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get all criminals as a one-time fetch
     */
    suspend fun getAllOnce(): List<CriminalRecord> {
        return withContext(Dispatchers.IO) {
            try {
                getCriminalsCollection()
                    .get()
                    .await()
                    .documents
                    .mapNotNull { document ->
                        document.toObject(CriminalRecord::class.java)?.copy(criminalID = document.id)
                    }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    /**
     * Update a criminal record
     */
    suspend fun updateCriminalRecord(criminalId: String, updates: Map<String, Any>) {
        withContext(Dispatchers.IO) {
            try {
                val updateMap = updates.toMutableMap()
                updateMap["lastUpdated"] = Timestamp.now()

                getCriminalsCollection()
                    .document(criminalId)
                    .update(updateMap)
                    .await()
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    /**
     * Increment the image count for a criminal
     */
    suspend fun incrementImageCount(criminalId: String) {
        withContext(Dispatchers.IO) {
            try {
                getCriminalsCollection()
                    .document(criminalId)
                    .update(
                        mapOf(
                            "numImages" to FieldValue.increment(1), // Changed from imageCount to numImages
                            "lastUpdated" to Timestamp.now()
                        )
                    )
                    .await()
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    /**
     * Decrement the image count for a criminal
     */
    suspend fun decrementImageCount(criminalId: String) {
        withContext(Dispatchers.IO) {
            try {
                getCriminalsCollection()
                    .document(criminalId)
                    .update(
                        mapOf(
                            "numImages" to FieldValue.increment(-1), // Changed from imageCount to numImages
                            "lastUpdated" to Timestamp.now()
                        )
                    )
                    .await()
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    /**
     * Remove a criminal record
     */
    suspend fun removeCriminalRecord(criminalId: String) {
        withContext(Dispatchers.IO) {
            try {
                getCriminalsCollection()
                    .document(criminalId)
                    .delete()
                    .await()
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    /**
     * Search criminals by name (uses criminalName field)
     */
    suspend fun searchByName(criminalName: String): List<CriminalRecord> {
        return withContext(Dispatchers.IO) {
            try {
                getCriminalsCollection()
                    .whereEqualTo("criminalName", criminalName)
                    .get()
                    .await()
                    .documents
                    .mapNotNull { document ->
                        document.toObject(CriminalRecord::class.java)?.copy(criminalID = document.id)
                    }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    /**
     * Search criminals by danger level
     */
    suspend fun searchByDangerLevel(dangerLevel: String): List<CriminalRecord> {
        return withContext(Dispatchers.IO) {
            try {
                getCriminalsCollection()
                    .whereEqualTo("dangerLevel", dangerLevel)
                    .get()
                    .await()
                    .documents
                    .mapNotNull { document ->
                        document.toObject(CriminalRecord::class.java)?.copy(criminalID = document.id)
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
    suspend fun existsByName(criminalName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val snapshot = getCriminalsCollection()
                    .whereEqualTo("criminalName", criminalName)
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
     * Get the total count of criminals
     */
    suspend fun getCount(): Long {
        return withContext(Dispatchers.IO) {
            try {
                getCriminalsCollection()
                    .get()
                    .await()
                    .size()
                    .toLong()
            } catch (e: Exception) {
                e.printStackTrace()
                0L
            }
        }
    }

    /**
     * Clear all criminal records
     */
    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            try {
                val allRecords = getCriminalsCollection().get().await()

                // Delete in batches of 500 (Firestore limit)
                val batches = allRecords.documents.chunked(500)

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

    /**
     * Search criminals by active status
     */
    suspend fun searchByActiveStatus(isActive: Boolean): List<CriminalRecord> {
        return withContext(Dispatchers.IO) {
            try {
                getCriminalsCollection()
                    .whereEqualTo("isActive", isActive)
                    .get()
                    .await()
                    .documents
                    .mapNotNull { document ->
                        document.toObject(CriminalRecord::class.java)?.copy(criminalID = document.id)
                    }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    /**
     * Get criminals by crime type
     */
    suspend fun searchByCrime(crime: String): List<CriminalRecord> {
        return withContext(Dispatchers.IO) {
            try {
                getCriminalsCollection()
                    .whereArrayContains("crimes", crime)
                    .get()
                    .await()
                    .documents
                    .mapNotNull { document ->
                        document.toObject(CriminalRecord::class.java)?.copy(criminalID = document.id)
                    }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
}