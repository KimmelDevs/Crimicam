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


class PersonDB(
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

    suspend fun addPerson(person: PersonRecord): String {
        return withContext(Dispatchers.IO) {
            try {
                val personWithTimestamp = person.copy(
                    createdAt = person.createdAt ?: Timestamp.now(),
                    lastUpdated = Timestamp.now()
                )

                if (person.personID.isEmpty()) {
                    // Auto-generate ID
                    val docRef = getPersonsCollection().add(personWithTimestamp).await()
                    docRef.id
                } else {
                    getPersonsCollection().document(person.personID).set(personWithTimestamp).await()
                    person.personID
                }
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    suspend fun removePerson(personID: String) {
        withContext(Dispatchers.IO) {
            try {
                getPersonsCollection().document(personID).delete().await()
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    suspend fun getPerson(personID: String): PersonRecord? {
        return withContext(Dispatchers.IO) {
            try {
                val document = getPersonsCollection().document(personID).get().await()
                document.toObject(PersonRecord::class.java)?.copy(
                    personID = document.id
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    suspend fun getCount(): Long {
        return withContext(Dispatchers.IO) {
            try {
                getPersonsCollection().get().await().size().toLong()
            } catch (e: Exception) {
                e.printStackTrace()
                0L
            }
        }
    }

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
                    trySend(persons)
                }
            }

        awaitClose {
            listenerRegistration.remove()
        }
    }.flowOn(Dispatchers.IO)

    suspend fun updatePerson(personID: String, updates: Map<String, Any>) {
        withContext(Dispatchers.IO) {
            try {
                val updatesWithTimestamp = updates.toMutableMap()
                updatesWithTimestamp["lastUpdated"] = Timestamp.now()

                getPersonsCollection().document(personID)
                    .update(updatesWithTimestamp)
                    .await()
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    suspend fun incrementImageCount(personID: String) {
        withContext(Dispatchers.IO) {
            try {
                getPersonsCollection().document(personID).update(
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

    suspend fun decrementImageCount(personID: String) {
        withContext(Dispatchers.IO) {
            try {
                getPersonsCollection().document(personID).update(
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
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }
}