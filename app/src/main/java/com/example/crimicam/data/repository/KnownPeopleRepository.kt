package com.example.crimicam.data.repository

import android.graphics.Bitmap
import com.example.crimicam.data.model.KnownPerson
import com.example.crimicam.data.model.PersonImage
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

    private fun getPersonImagesCollection(personId: String) = auth.currentUser?.uid?.let { userId ->
        firestore.collection("users")
            .document(userId)
            .collection("known_people")
            .document(personId)
            .collection("images")
    }

    /**
     * Create a new known person and add their first image
     * Structure: users/{userId}/known_people/{personId}
     *            users/{userId}/known_people/{personId}/images/{imageId}
     */
    suspend fun addKnownPersonWithImage(
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

            // Generate person document ID
            val personDocId = collection.document().id

            // Create the person document (without image data)
            val knownPerson = KnownPerson(
                id = personDocId,
                userId = userId,
                name = name,
                description = description,
                imageCount = 1,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            // Save person to Firestore
            collection.document(personDocId).set(knownPerson).await()

            // Add the first image to subcollection
            addImageToPersonInternal(personDocId, originalBitmap, croppedFaceBitmap, faceFeatures)

            Result.Success(knownPerson)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Add another image to an existing person
     */
    suspend fun addImageToPerson(
        personId: String,
        originalBitmap: Bitmap,
        croppedFaceBitmap: Bitmap,
        faceFeatures: Map<String, Float>
    ): Result<PersonImage> {
        return try {
            val image = addImageToPersonInternal(personId, originalBitmap, croppedFaceBitmap, faceFeatures)

            // Update person's image count
            val personCollection = getUserKnownPeopleCollection()
                ?: return Result.Error(Exception("Unable to access user collection"))

            val personDoc = personCollection.document(personId).get().await()
            val currentCount = personDoc.getLong("imageCount")?.toInt() ?: 0

            personCollection.document(personId).update(
                mapOf(
                    "imageCount" to currentCount + 1,
                    "updatedAt" to System.currentTimeMillis()
                )
            ).await()

            Result.Success(image)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    private suspend fun addImageToPersonInternal(
        personId: String,
        originalBitmap: Bitmap,
        croppedFaceBitmap: Bitmap,
        faceFeatures: Map<String, Float>
    ): PersonImage {
        val imagesCollection = getPersonImagesCollection(personId)
            ?: throw Exception("Unable to access images collection")

        // Compress images
        val compressedOriginal = ImageCompressor.compressBitmap(originalBitmap)
        val originalBase64 = ImageCompressor.bitmapToBase64(compressedOriginal, quality = 60)

        val compressedFace = ImageCompressor.compressFaceCrop(croppedFaceBitmap)
        val faceBase64 = ImageCompressor.bitmapToBase64(compressedFace, quality = 80)

        // Generate image document ID
        val imageDocId = imagesCollection.document().id

        val personImage = PersonImage(
            id = imageDocId,
            personId = personId,
            originalImageBase64 = originalBase64,
            croppedFaceBase64 = faceBase64,
            faceFeatures = faceFeatures,
            createdAt = System.currentTimeMillis()
        )

        // Save to subcollection
        imagesCollection.document(imageDocId).set(personImage).await()

        return personImage
    }

    /**
     * Get all known people (without loading all their images)
     */
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

    /**
     * Get all images for a specific person
     */
    suspend fun getPersonImages(personId: String): Result<List<PersonImage>> {
        return try {
            val collection = getPersonImagesCollection(personId)
                ?: return Result.Error(Exception("User not logged in"))

            val snapshot = collection
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val images = snapshot.documents.mapNotNull {
                it.toObject(PersonImage::class.java)
            }
            Result.Success(images)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Get the first (thumbnail) image for a person
     */
    suspend fun getPersonThumbnail(personId: String): Result<PersonImage?> {
        return try {
            val collection = getPersonImagesCollection(personId)
                ?: return Result.Error(Exception("User not logged in"))

            val snapshot = collection
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .limit(1)
                .get()
                .await()

            val image = snapshot.documents.firstOrNull()?.toObject(PersonImage::class.java)
            Result.Success(image)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Delete a person and all their images
     */
    suspend fun deleteKnownPerson(personId: String): Result<Unit> {
        return try {
            val collection = getUserKnownPeopleCollection()
                ?: return Result.Error(Exception("User not logged in"))

            val imagesCollection = getPersonImagesCollection(personId)
                ?: return Result.Error(Exception("Unable to access images collection"))

            // Delete all images in subcollection
            val images = imagesCollection.get().await()
            images.documents.forEach { it.reference.delete().await() }

            // Delete the person document
            collection.document(personId).delete().await()

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Delete a specific image from a person
     */
    suspend fun deletePersonImage(personId: String, imageId: String): Result<Unit> {
        return try {
            val imagesCollection = getPersonImagesCollection(personId)
                ?: return Result.Error(Exception("User not logged in"))

            val personCollection = getUserKnownPeopleCollection()
                ?: return Result.Error(Exception("Unable to access user collection"))

            // Delete the image
            imagesCollection.document(imageId).delete().await()

            // Update person's image count
            val personDoc = personCollection.document(personId).get().await()
            val currentCount = personDoc.getLong("imageCount")?.toInt() ?: 0

            personCollection.document(personId).update(
                mapOf(
                    "imageCount" to (currentCount - 1).coerceAtLeast(0),
                    "updatedAt" to System.currentTimeMillis()
                )
            ).await()

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Update person details (name, description)
     */
    suspend fun updateKnownPerson(personId: String, name: String, description: String): Result<Unit> {
        return try {
            val collection = getUserKnownPeopleCollection()
                ?: return Result.Error(Exception("User not logged in"))

            collection.document(personId).update(
                mapOf(
                    "name" to name,
                    "description" to description,
                    "updatedAt" to System.currentTimeMillis()
                )
            ).await()

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}