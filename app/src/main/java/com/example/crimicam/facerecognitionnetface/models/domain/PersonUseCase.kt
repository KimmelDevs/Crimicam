package com.example.crimicam.facerecognitionnetface.models.domain

import com.example.crimicam.facerecognitionnetface.models.data.PersonDB
import com.example.crimicam.facerecognitionnetface.models.data.PersonRecord
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Single

@Single
class PersonUseCase(
    private val personDB: PersonDB
) {
    suspend fun addPerson(name: String, numImages: Long): String {
        val personRecord = PersonRecord(
            personName = name,
            numImages = numImages,
            imageUris = emptyList(), // Initialize with empty list
            embeddings = emptyList() // Initialize with empty list
        )
        return personDB.addPerson(personRecord)
    }

    suspend fun addPersonWithId(personId: String, name: String, numImages: Long): String {
        val personRecord = PersonRecord(
            personID = personId,
            personName = name,
            numImages = numImages,
            imageUris = emptyList(),
            embeddings = emptyList()
        )
        return personDB.addPerson(personRecord)
    }

    suspend fun removePerson(personId: String) {
        personDB.removePerson(personId)
    }

    fun getAll(): Flow<List<PersonRecord>> {
        return personDB.getAll()
    }

    suspend fun getPerson(personId: String): PersonRecord? {
        // You might need to add this method to your PersonDB
        return try {
            personDB.getPerson(personId)
        } catch (e: Exception) {
            null
        }
    }
}