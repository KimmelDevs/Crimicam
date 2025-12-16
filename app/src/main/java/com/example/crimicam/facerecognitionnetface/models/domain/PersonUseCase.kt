package com.example.crimicam.facerecognitionnetface.models.domain

import com.example.crimicam.facerecognitionnetface.models.data.PersonRecord
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

class PersonUseCase(
    private val currentUserId: String
) {
    private val firestore = FirebaseFirestore.getInstance()

    // Dynamic subcollection path based on current user
    private fun getPersonsCollection(): CollectionReference {
        return firestore
            .collection("users")
            .document(currentUserId)
            .collection("persons")
    }

    // Cache for count to avoid repeated queries
    private var cachedCount: Long = 0L
    private var lastCountUpdate: Long = 0L
    private val countCacheTimeout = 5000L // 5 seconds

    /**
     * Add a new person to the database
     * Returns the auto-generated person ID
     */
    suspend fun addPerson(
        name: String,
        numImages: Long = 0
    ): String {
        return withContext(Dispatchers.IO) {
            try {
                val personRecord = PersonRecord(
                    personID = "", // Will be auto-generated
                    personName = name,
                    numImages = numImages,
                    createdAt = Timestamp.now(),
                    lastUpdated = Timestamp.now()
                )

                val docRef = getPersonsCollection().add(personRecord).await()
                invalidateCountCache()
                docRef.id
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    /**
     * Update an existing person's data
     */
    suspend fun updatePerson(
        personId: String,
        updates: Map<String, Any>
    ) {
        withContext(Dispatchers.IO) {
            try {
                val updatesWithTimestamp = updates.toMutableMap()
                updatesWithTimestamp["lastUpdated"] = Timestamp.now()

                getPersonsCollection().document(personId)
                    .update(updatesWithTimestamp)
                    .await()
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    /**
     * Increment the image count for a person atomically
     */
    suspend fun incrementImageCount(personId: String) {
        withContext(Dispatchers.IO) {
            try {
                getPersonsCollection().document(personId).update(
                    mapOf(
                        "numImages" to FieldValue.increment(1),
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
     * Decrement the image count for a person atomically
     */
    suspend fun decrementImageCount(personId: String) {
        withContext(Dispatchers.IO) {
            try {
                getPersonsCollection().document(personId).update(
                    mapOf(
                        "numImages" to FieldValue.increment(-1),
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
     * Get a single person by ID
     */
    suspend fun getPerson(personId: String): PersonRecord? {
        return withContext(Dispatchers.IO) {
            try {
                val document = getPersonsCollection().document(personId).get().await()
                document.toObject(PersonRecord::class.java)?.copy(
                    personID = document.id
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Get all persons as a Flow (reactive)
     */
    fun getAll(): Flow<List<PersonRecord>> = callbackFlow {
        val listenerRegistration = getPersonsCollection()
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val persons = snapshot.documents.mapNotNull { document ->
                        document.toObject(PersonRecord::class.java)?.copy(
                            personID = document.id
                        )
                    }

                    // Update cached count
                    cachedCount = persons.size.toLong()
                    lastCountUpdate = System.currentTimeMillis()

                    trySend(persons)
                }
            }

        awaitClose {
            listenerRegistration.remove()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get all persons as a list (one-time fetch)
     */
    suspend fun getAllOnce(): List<PersonRecord> {
        return withContext(Dispatchers.IO) {
            try {
                val snapshot = getPersonsCollection().get().await()
                val persons = snapshot.documents.mapNotNull { document ->
                    document.toObject(PersonRecord::class.java)?.copy(
                        personID = document.id
                    )
                }

                // Update cached count
                cachedCount = persons.size.toLong()
                lastCountUpdate = System.currentTimeMillis()

                persons
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    /**
     * Get the total count of persons in the database
     * Uses caching to avoid repeated queries
     */
    fun getCount(): Long {
        val currentTime = System.currentTimeMillis()

        // Return cached count if it's still valid
        if (cachedCount > 0 && (currentTime - lastCountUpdate) < countCacheTimeout) {
            return cachedCount
        }

        // If cache is invalid, trigger a background update
        // but return the cached value for now
        return cachedCount
    }

    /**
     * Force refresh the count from Firestore
     */
    suspend fun refreshCount(): Long {
        return withContext(Dispatchers.IO) {
            try {
                val snapshot = getPersonsCollection().get().await()
                cachedCount = snapshot.size().toLong()
                lastCountUpdate = System.currentTimeMillis()
                cachedCount
            } catch (e: Exception) {
                e.printStackTrace()
                0L
            }
        }
    }

    /**
     * Remove a person from the database
     */
    suspend fun removePerson(personId: String) {
        withContext(Dispatchers.IO) {
            try {
                getPersonsCollection().document(personId).delete().await()
                invalidateCountCache()
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    /**
     * Search for persons by name
     */
    suspend fun searchByName(name: String): List<PersonRecord> {
        return withContext(Dispatchers.IO) {
            try {
                val snapshot = getPersonsCollection()
                    .whereEqualTo("personName", name)
                    .get()
                    .await()

                snapshot.documents.mapNotNull { document ->
                    document.toObject(PersonRecord::class.java)?.copy(
                        personID = document.id
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    /**
     * Check if a person with the given name exists
     */
    suspend fun existsByName(name: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val snapshot = getPersonsCollection()
                    .whereEqualTo("personName", name)
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
     * Clear all persons from the database
     */
    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            try {
                val snapshot = getPersonsCollection().get().await()

                // Delete in batches of 500 (Firestore limit)
                val batches = snapshot.documents.chunked(500)

                batches.forEach { batch ->
                    val deleteBatch = firestore.batch()
                    batch.forEach { document ->
                        deleteBatch.delete(document.reference)
                    }
                    deleteBatch.commit().await()
                }

                invalidateCountCache()
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    /**
     * Invalidate the cached count
     */
    private fun invalidateCountCache() {
        lastCountUpdate = 0L
    }
}