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
class CriminalImagesVectorDB {
    private val firestore = FirebaseFirestore.getInstance()

    // Global collection - NOT a subcollection
    private fun getCriminalImagesCollection(): CollectionReference {
        return firestore.collection("criminal_face_images")
    }

    // ==================== NEW METHODS FOR FaceImageRecord ====================

    /**
     * Add a face image record (used by CriminalUseCase)
     */
    suspend fun addFaceImageRecord(record: FaceImageRecord): String {
        return withContext(Dispatchers.IO) {
            try {
                if (record.recordID.isEmpty()) {
                    val docRef = getCriminalImagesCollection().add(record).await()
                    docRef.id
                } else {
                    getCriminalImagesCollection().document(record.recordID).set(record).await()
                    record.recordID
                }
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    /**
     * Get all face records for a specific person/criminal ID
     */
    suspend fun getFaceRecordsByPersonID(personID: String): List<FaceImageRecord> {
        return withContext(Dispatchers.IO) {
            try {
                getCriminalImagesCollection()
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

    /**
     * Remove all face records for a specific person/criminal ID
     */
    suspend fun removeFaceRecordsWithPersonID(personID: String) {
        withContext(Dispatchers.IO) {
            try {
                val querySnapshot = getCriminalImagesCollection()
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

    /**
     * Get nearest embedding match for face recognition
     * Returns FaceImageRecord instead of CriminalImageRecord
     */
    suspend fun getNearestFaceEmbedding(
        embedding: FloatArray,
        confidenceThreshold: Float = 0.6f
    ): Pair<FaceImageRecord?, Float> {
        return withContext(Dispatchers.IO) {
            try {
                val allRecords = getAllFaceImageRecords()

                if (allRecords.isEmpty()) {
                    return@withContext Pair(null, 0f)
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

                // Find the best match across all batches
                val (bestRecord, bestScore) = results.maxByOrNull { it.second }
                    ?: return@withContext Pair(null, 0f)

                // Apply confidence threshold
                if (bestScore > confidenceThreshold) {
                    Pair(bestRecord, bestScore)
                } else {
                    Pair(null, bestScore)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Pair(null, 0f)
            }
        }
    }

    /**
     * Get all face image records
     */
    private suspend fun getAllFaceImageRecords(): List<FaceImageRecord> {
        return try {
            getCriminalImagesCollection()
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

    // ==================== ORIGINAL METHODS FOR CriminalImageRecord ====================

    suspend fun addCriminalImageRecord(record: CriminalImageRecord): String {
        return withContext(Dispatchers.IO) {
            try {
                if (record.recordID.isEmpty()) {
                    val docRef = getCriminalImagesCollection().add(record).await()
                    docRef.id
                } else {
                    getCriminalImagesCollection().document(record.recordID).set(record).await()
                    record.recordID
                }
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    suspend fun getNearestEmbeddingCriminalName(
        embedding: FloatArray,
        flatSearch: Boolean = true,
    ): CriminalImageRecord? {
        return withContext(Dispatchers.IO) {
            try {
                val allRecords = getAllCriminalImageRecords()

                if (allRecords.isEmpty()) {
                    return@withContext null
                }

                val numThreads = 4
                val batchSize = if (allRecords.isEmpty()) 1 else allRecords.size / numThreads
                val batches = allRecords.chunked(batchSize.coerceAtLeast(1))

                val results = batches
                    .map { batch ->
                        async(Dispatchers.Default) {
                            var bestMatch: CriminalImageRecord? = null
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

                val (bestRecord, bestScore) = results.maxByOrNull { it.second }
                    ?: return@withContext null

                if (bestScore > 0.6f) {
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
    ): List<Pair<CriminalImageRecord, Float>> {
        return withContext(Dispatchers.IO) {
            try {
                val allRecords = getAllCriminalImageRecords()

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

    private suspend fun getAllCriminalImageRecords(): List<CriminalImageRecord> {
        return try {
            getCriminalImagesCollection()
                .get()
                .await()
                .documents
                .mapNotNull { document ->
                    document.toObject(CriminalImageRecord::class.java)?.copy(
                        recordID = document.id
                    )
                }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun getAllCriminalImageRecordsFlow(): Flow<List<CriminalImageRecord>> = callbackFlow {
        val listenerRegistration = getCriminalImagesCollection().addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val records = snapshot.documents.mapNotNull { document ->
                    document.toObject(CriminalImageRecord::class.java)?.copy(
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

    suspend fun getCriminalRecordsByCriminalID(criminalID: String): List<CriminalImageRecord> {
        return withContext(Dispatchers.IO) {
            try {
                getCriminalImagesCollection()
                    .whereEqualTo("criminalID", criminalID)
                    .get()
                    .await()
                    .documents
                    .mapNotNull { document ->
                        document.toObject(CriminalImageRecord::class.java)?.copy(
                            recordID = document.id
                        )
                    }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    suspend fun removeCriminalRecordsWithCriminalID(criminalID: String) {
        withContext(Dispatchers.IO) {
            try {
                val querySnapshot = getCriminalImagesCollection()
                    .whereEqualTo("criminalID", criminalID)
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

    suspend fun removeCriminalRecord(recordID: String) {
        withContext(Dispatchers.IO) {
            try {
                getCriminalImagesCollection().document(recordID).delete().await()
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    suspend fun clearAllCriminalRecords() {
        withContext(Dispatchers.IO) {
            try {
                val allRecords = getCriminalImagesCollection().get().await()

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

    suspend fun getAllUniqueCriminalNames(): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val allRecords = getAllCriminalImageRecords()
                allRecords.map { it.criminalName }.distinct()
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    suspend fun getCriminalRecordCount(): Long {
        return withContext(Dispatchers.IO) {
            try {
                getCriminalImagesCollection().get().await().size().toLong()
            } catch (e: Exception) {
                e.printStackTrace()
                0L
            }
        }
    }

    suspend fun getCriminalImageRecordCount(criminalID: String): Long {
        return withContext(Dispatchers.IO) {
            try {
                getCriminalImagesCollection()
                    .whereEqualTo("criminalID", criminalID)
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
}
