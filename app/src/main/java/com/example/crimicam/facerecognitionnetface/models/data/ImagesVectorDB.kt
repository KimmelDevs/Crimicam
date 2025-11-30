package com.example.crimicam.facerecognitionnetface.models.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import kotlin.math.sqrt

@Single
class ImagesVectorDB(
    private val currentUserId: String
) {
    private val firestore = FirebaseFirestore.getInstance()

    // Dynamic subcollection path based on current user
    private fun getImagesCollection(): CollectionReference {
        return firestore
            .collection("users")
            .document(currentUserId)
            .collection("face_images")
    }

    suspend fun addFaceImageRecord(record: FaceImageRecord): String {
        return withContext(Dispatchers.IO) {
            try {
                if (record.recordID.isEmpty()) {
                    // Auto-generate ID and return it
                    val docRef = getImagesCollection().add(record).await()
                    docRef.id
                } else {
                    getImagesCollection().document(record.recordID).set(record).await()
                    record.recordID
                }
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    suspend fun getNearestEmbeddingPersonName(
        embedding: FloatArray,
        flatSearch: Boolean = true,
    ): FaceImageRecord? {
        return withContext(Dispatchers.IO) {
            try {
                // Firestore doesn't support native vector search, so we always use linear search
                val allRecords = getAllFaceImageRecords()

                if (allRecords.isEmpty()) {
                    return@withContext null
                }

                // Use parallel processing for better performance with large datasets
                val numThreads = 4
                val batchSize = if (allRecords.isEmpty()) 1 else allRecords.size / numThreads
                val batches = allRecords.chunked(batchSize.coerceAtLeast(1))

                val results = batches
                    .map { batch ->
                        async(Dispatchers.Default) {
                            var bestMatch: FaceImageRecord? = null
                            var bestSimilarity = -1.0f // Cosine similarity ranges from -1 to 1

                            for (record in batch) {
                                val recordEmbedding = record.faceEmbedding.toFloatArray()
                                val similarity = cosineSimilarity(embedding, recordEmbedding)

                                if (similarity > bestSimilarity) {
                                    bestSimilarity = similarity
                                    bestMatch = record
                                }
                            }
                            Pair(bestMatch, bestSimilarity)
                        }
                    }.awaitAll()

                // Find the best match across all batches
                val (bestRecord, bestScore) = results.maxByOrNull { it.second } ?: return@withContext null

                // Apply confidence threshold (you can adjust this)
                if (bestScore > 0.6f) { // 0.6 is a reasonable threshold for face recognition
                    bestRecord
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    suspend fun getNearestEmbeddings(
        embedding: FloatArray,
        limit: Int = 5,
        minConfidence: Float = 0.5f
    ): List<Pair<FaceImageRecord, Float>> {
        return withContext(Dispatchers.IO) {
            try {
                val allRecords = getAllFaceImageRecords()

                val similarities = allRecords.map { record ->
                    val recordEmbedding = record.faceEmbedding.toFloatArray()
                    val similarity = cosineSimilarity(embedding, recordEmbedding)
                    record to similarity
                }

                similarities
                    .filter { it.second >= minConfidence }
                    .sortedByDescending { it.second }
                    .take(limit)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    private suspend fun getAllFaceImageRecords(): List<FaceImageRecord> {
        return try {
            getImagesCollection()
                .get()
                .await()
                .documents
                .mapNotNull { document ->
                    document.toObject(FaceImageRecord::class.java)?.copy(
                        recordID = document.id // Ensure the document ID is set
                    )
                }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun getAllFaceImageRecordsFlow(): Flow<List<FaceImageRecord>> = callbackFlow {
        val listenerRegistration = getImagesCollection().addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val records = snapshot.documents.mapNotNull { document ->
                    document.toObject(FaceImageRecord::class.java)?.copy(
                        recordID = document.id
                    )
                }
                trySend(records)
            }
        }

        awaitClose {
            listenerRegistration.remove()
        }
    }.flowOn(Dispatchers.IO)

    suspend fun getFaceRecordsByPersonID(personID: String): List<FaceImageRecord> {
        return withContext(Dispatchers.IO) {
            try {
                getImagesCollection()
                    .whereEqualTo("personID", personID)
                    .get()
                    .await()
                    .documents
                    .mapNotNull { document ->
                        document.toObject(FaceImageRecord::class.java)?.copy(
                            recordID = document.id
                        )
                    }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    suspend fun removeFaceRecordsWithPersonID(personID: String) {
        withContext(Dispatchers.IO) {
            try {
                val querySnapshot = getImagesCollection()
                    .whereEqualTo("personID", personID)
                    .get()
                    .await()

                // Batch delete for efficiency
                val batch = firestore.batch()
                querySnapshot.documents.forEach { document ->
                    batch.delete(document.reference)
                }
                batch.commit().await()
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    suspend fun removeFaceRecord(recordID: String) {
        withContext(Dispatchers.IO) {
            try {
                getImagesCollection().document(recordID).delete().await()
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    suspend fun clearAllFaceRecords() {
        withContext(Dispatchers.IO) {
            try {
                val allRecords = getImagesCollection().get().await()

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

    suspend fun getAllUniquePersonNames(): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val allRecords = getAllFaceImageRecords()
                allRecords.map { it.personName }.distinct()
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    suspend fun getFaceRecordCount(): Long {
        return withContext(Dispatchers.IO) {
            try {
                getImagesCollection().get().await().size().toLong()
            } catch (e: Exception) {
                e.printStackTrace()
                0L
            }
        }
    }

    suspend fun getPersonFaceRecordCount(personID: String): Long {
        return withContext(Dispatchers.IO) {
            try {
                getImagesCollection()
                    .whereEqualTo("personID", personID)
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

    private fun cosineSimilarity(
        x1: FloatArray,
        x2: FloatArray,
    ): Float {
        require(x1.size == x2.size) { "Embedding dimensions must match" }

        var dotProduct = 0.0f
        var norm1 = 0.0f
        var norm2 = 0.0f

        for (i in x1.indices) {
            dotProduct += x1[i] * x2[i]
            norm1 += x1[i] * x1[i]
            norm2 += x2[i] * x2[i]
        }

        norm1 = sqrt(norm1)
        norm2 = sqrt(norm2)

        return if (norm1 > 0 && norm2 > 0) {
            dotProduct / (norm1 * norm2)
        } else {
            0.0f
        }
    }

    private fun euclideanDistance(
        x1: FloatArray,
        x2: FloatArray,
    ): Float {
        require(x1.size == x2.size) { "Embedding dimensions must match" }

        var sum = 0.0f
        for (i in x1.indices) {
            val diff = x1[i] - x2[i]
            sum += diff * diff
        }
        return sqrt(sum)
    }

    // Utility function to convert distance to similarity score
    private fun distanceToSimilarity(distance: Float, maxDistance: Float = 2.0f): Float {
        return 1.0f - (distance / maxDistance).coerceIn(0.0f, 1.0f)
    }
}