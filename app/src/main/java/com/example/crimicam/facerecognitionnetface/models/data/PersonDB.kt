package com.example.crimicam.facerecognitionnetface.models.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
@Single
class PersonDB {
    private val firestore = FirebaseFirestore.getInstance()
    private val personCollection = firestore.collection("persons")

    suspend fun addPerson(person: PersonRecord): String {
        return withContext(Dispatchers.IO) {
            if (person.personID.isEmpty()) {
                // Auto-generate ID
                val docRef = personCollection.add(person).await()
                docRef.id
            } else {
                personCollection.document(person.personID).set(person).await()
                person.personID
            }
        }
    }

    suspend fun removePerson(personID: String) {
        withContext(Dispatchers.IO) {
            personCollection.document(personID).delete().await()
        }
    }

    suspend fun getPerson(personID: String): PersonRecord? {
        return withContext(Dispatchers.IO) {
            try {
                personCollection.document(personID).get().await().toObject(PersonRecord::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun getCount(): Long {
        return withContext(Dispatchers.IO) {
            try {
                personCollection.get().await().size().toLong()
            } catch (e: Exception) {
                0L
            }
        }
    }

    fun getAll(): Flow<List<PersonRecord>> = callbackFlow {
        val listenerRegistration = personCollection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val persons = snapshot.documents.mapNotNull { document ->
                    document.toObject(PersonRecord::class.java)?.copy(
                        personID = document.id // Ensure the document ID is set
                    )
                }
                trySend(persons)
            }
        }

        awaitClose {
            listenerRegistration.remove()
        }
    }.flowOn(Dispatchers.IO)
}