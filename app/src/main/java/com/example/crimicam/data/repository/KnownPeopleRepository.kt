package com.example.crimicam.data.repository

import android.graphics.Bitmap
import com.example.crimicam.data.model.KnownPerson
import com.example.crimicam.util.ImageCompressor
import com.example.crimicam.util.Result
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class KnownPeopleRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private fun getUserKnownPeopleCollection() = auth.currentUser?.uid?.let { userId ->
        firestore.collection("users")
            .document(userId)
            .collection("known_people")
    }

    suspend fun addKnownPerson(
        name: String,
        description: String,
        originalBitmap: Bitmap,
        croppedFaceBitmap: Bitmap,
        faceFeatures: Map<String, Float>
    ): Result<KnownPerson> {
        return try {
            val userId = auth.currentUser?.uid
                ?: return Result.Error(Exception("User not logged in"))

            val collection = getUserKnownPeopleCollection()
                ?: return Result.Error(Exception("Unable to access user collection"))

            // Compress original image
            val compressedOriginal = ImageCompressor.compressBitmap(originalBitmap)
            val originalBase64 = ImageCompressor.bitmapToBase64(compressedOriginal, quality = 60)

            // Compress face crop
            val compressedFace = ImageCompressor.compressFaceCrop(croppedFaceBitmap)
            val faceBase64 = ImageCompressor.bitmapToBase64(compressedFace, quality = 80)

            // Generate new document ID
            val docId = collection.document().id

            val knownPerson = KnownPerson(
                id = docId,
                userId = userId,
                name = name,
                description = description,
                originalImageBase64 = originalBase64,
                croppedFaceBase64 = faceBase64,
                faceFeatures = faceFeatures,
                imageCount = 1
            )

            // Save to Firestore under users/{userId}/known_people/{docId}
            collection.document(docId)
                .set(knownPerson)
                .await()

            Result.Success(knownPerson)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getKnownPeople(): Result<List<KnownPerson>> {
        return try {
            val collection = getUserKnownPeopleCollection()
                ?: return Result.Error(Exception("User not logged in"))

            val snapshot = collection
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val people = snapshot.documents.mapNotNull {
                it.toObject(KnownPerson::class.java)
            }
            Result.Success(people)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun deleteKnownPerson(personId: String): Result<Unit> {
        return try {
            val collection = getUserKnownPeopleCollection()
                ?: return Result.Error(Exception("User not logged in"))

            collection.document(personId)
                .delete()
                .await()

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun updateKnownPerson(person: KnownPerson): Result<KnownPerson> {
        return try {
            val collection = getUserKnownPeopleCollection()
                ?: return Result.Error(Exception("User not logged in"))

            val updatedPerson = person.copy(updatedAt = System.currentTimeMillis())

            collection.document(person.id)
                .set(updatedPerson)
                .await()

            Result.Success(updatedPerson)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getKnownPersonById(personId: String): Result<KnownPerson> {
        return try {
            val collection = getUserKnownPeopleCollection()
                ?: return Result.Error(Exception("User not logged in"))

            val snapshot = collection.document(personId)
                .get()
                .await()

            val person = snapshot.toObject(KnownPerson::class.java)
                ?: return Result.Error(Exception("Person not found"))

            Result.Success(person)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}