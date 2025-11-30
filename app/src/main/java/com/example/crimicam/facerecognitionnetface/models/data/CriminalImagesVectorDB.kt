package com.example.crimicam.facerecognitionnetface.models.data

import com.google.firebase.Timestamp
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

/**
 * Manages face embeddings for criminals in a global collection
 * (not user-specific, accessible by all users)
 */
@Single
class CriminalImagesVectorDB {
    private val firestore = FirebaseFirestore.getInstance()

    // Global collection for criminal face images
    private val imagesCollection = firestore.collection("criminal_face_images")

    suspend fun addFaceImageRecord(record: FaceImageRecord): String {
        return withContext(Dispatchers.IO) {
            try {
                if (record.recordID.isEmpty()) {
                    val docRef = imagesCollection.add(record).await()
                    docRef.id
                } else {
                    imagesCollection.document(record.recordID).set(record).await()
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
                val allRecords = getAllFaceImageRecords()

                if (allRecords.isEmpty()) {
                    return@withContext null
                }

                // Use parallel processing for better performance
                val numThreads = 4
                val batchSize = if (allRecords.isEmpty()) 1 else allRecords.size / numThreads
                val batches = allRecords.chunked(batchSize.coerceAtLeast(1))

                val results = batches
                    .map { batch ->
                        async(Dispatchers.Default) {
                            var bestMatch: FaceImageRecord? = null
                            var bestSimilarity = -1.0f

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

                val (bestRecord, bestScore) = results.maxByOrNull { it.second } ?: return@withContext null

                // Criminal face recognition threshold (slightly higher for security)
                if (bestScore > 0.65f) {
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
        minConfidence: Float = 0.6f
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
            imagesCollection
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

    fun getAllFaceImageRecordsFlow(): Flow<List<FaceImageRecord>> = callbackFlow {
        val listenerRegistration = imagesCollection.addSnapshotListener { snapshot, error ->
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

    suspend fun getFaceRecordsByPersonID(criminalId: String): List<FaceImageRecord> {
        return withContext(Dispatchers.IO) {
            try {
                imagesCollection
                    .whereEqualTo("personID", criminalId)
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

    suspend fun removeFaceRecordsWithPersonID(criminalId: String) {
        withContext(Dispatchers.IO) {
            try {
                val querySnapshot = imagesCollection
                    .whereEqualTo("personID", criminalId)
                    .get()
                    .await()

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
                imagesCollection.document(recordID).delete().await()
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    suspend fun clearAllFaceRecords() {
        withContext(Dispatchers.IO) {
            try {
                val allRecords = imagesCollection.get().await()

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

    suspend fun getFaceRecordCount(): Long {
        return withContext(Dispatchers.IO) {
            try {
                imagesCollection.get().await().size().toLong()
            } catch (e: Exception) {
                e.printStackTrace()
                0L
            }
        }
    }

    suspend fun getPersonFaceRecordCount(criminalId: String): Long {
        return withContext(Dispatchers.IO) {
            try {
                imagesCollection
                    .whereEqualTo("personID", criminalId)
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

    private fun cosineSimilarity(x1: FloatArray, x2: FloatArray): Float {
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
}