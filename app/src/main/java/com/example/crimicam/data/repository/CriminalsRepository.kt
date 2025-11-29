package com.example.crimicam.data.repository

import android.graphics.Bitmap
import com.example.crimicam.data.model.Criminal
import com.example.crimicam.data.model.Crime
import com.example.crimicam.data.model.CriminalImage
import com.example.crimicam.data.model.Warrant
import com.example.crimicam.util.ImageCompressor
import com.example.crimicam.util.Result
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class CriminalsRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    // Main collection for all criminals (shared across police department)
    private val criminalsCollection = firestore.collection("criminals")
    private val crimesCollection = firestore.collection("crimes")
    private val warrantsCollection = firestore.collection("warrants")
    private val searchAuditCollection = firestore.collection("search_audit_log")

    // ============================================
    // CRIMINAL MANAGEMENT
    // ============================================

    suspend fun addCriminal(
        firstName: String,
        lastName: String,
        middleName: String = "",
        aliases: List<String> = emptyList(),
        dateOfBirth: String,
        gender: String,
        nationality: String,
        nationalId: String = "",

        // Physical Description
        height: Int,
        weight: Int,
        eyeColor: String,
        hairColor: String,
        build: String,
        skinTone: String,
        distinguishingMarks: String = "",
        tattooDescriptions: List<String> = emptyList(),

        // Contact & Location
        lastKnownAddress: String = "",
        currentCity: String = "",
        currentProvince: String = "",
        phoneNumbers: List<String> = emptyList(),

        // Status
        status: String, // Active, Arrested, Wanted, Released, Deceased
        riskLevel: String, // Low, Medium, High, Extreme
        gangAffiliation: String = "",
        isArmed: Boolean = false,
        isDangerous: Boolean = false,

        // Images
        mugshotBitmap: Bitmap?,
        croppedFaceBitmap: Bitmap,
        faceFeatures: Map<String, Float>,

        // Admin
        notes: String = ""
    ): Result<Criminal> {
        return try {
            val officerId = auth.currentUser?.uid
                ?: return Result.Error(Exception("Officer not logged in"))

            // Compress and encode images
            val mugshotBase64 = mugshotBitmap?.let {
                val compressed = ImageCompressor.compressBitmap(it)
                ImageCompressor.bitmapToBase64(compressed, quality = 70)
            }

            val compressedFace = ImageCompressor.compressFaceCrop(croppedFaceBitmap)
            val faceBase64 = ImageCompressor.bitmapToBase64(compressedFace, quality = 85)

            // Generate new criminal ID
            val docId = criminalsCollection.document().id
            val subjectId = "SUB-${System.currentTimeMillis()}-${docId.take(6).uppercase()}"

            val criminal = Criminal(
                id = docId,
                subjectId = subjectId,

                // Personal Info
                firstName = firstName,
                lastName = lastName,
                middleName = middleName,
                aliases = aliases,
                dateOfBirth = dateOfBirth,
                gender = gender,
                nationality = nationality,
                nationalId = nationalId,

                // Physical Description
                height = height,
                weight = weight,
                eyeColor = eyeColor,
                hairColor = hairColor,
                build = build,
                skinTone = skinTone,
                distinguishingMarks = distinguishingMarks,
                tattooDescriptions = tattooDescriptions,

                // Contact & Location
                lastKnownAddress = lastKnownAddress,
                currentCity = currentCity,
                currentProvince = currentProvince,
                phoneNumbers = phoneNumbers,

                // Status
                status = status,
                riskLevel = riskLevel,
                gangAffiliation = gangAffiliation,
                isArmed = isArmed,
                isDangerous = isDangerous,

                // Images & Biometrics
                mugshotBase64 = mugshotBase64,
                croppedFaceBase64 = faceBase64,
                faceFeatures = faceFeatures,

                // Admin
                createdByOfficerId = officerId,
                lastUpdatedByOfficerId = officerId,
                notes = notes,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            // Save to Firestore
            criminalsCollection.document(docId)
                .set(criminal)
                .await()

            Result.Success(criminal)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun updateCriminal(criminal: Criminal): Result<Criminal> {
        return try {
            val officerId = auth.currentUser?.uid
                ?: return Result.Error(Exception("Officer not logged in"))

            val updatedCriminal = criminal.copy(
                lastUpdatedByOfficerId = officerId,
                updatedAt = System.currentTimeMillis()
            )

            criminalsCollection.document(criminal.id)
                .set(updatedCriminal)
                .await()

            Result.Success(updatedCriminal)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    private fun getCriminalImagesCollection(criminalId: String) =
        criminalsCollection.document(criminalId).collection("images")

    suspend fun addCriminalWithImages(
        // Personal Information
        firstName: String,
        lastName: String,
        middleName: String = "",
        aliases: List<String> = emptyList(),
        dateOfBirth: String,
        gender: String,
        nationality: String,
        nationalId: String = "",

        // Physical Description
        height: Int,
        weight: Int,
        eyeColor: String,
        hairColor: String,
        build: String,
        skinTone: String,
        distinguishingMarks: String = "",
        tattooDescriptions: List<String> = emptyList(),

        // Contact & Location
        lastKnownAddress: String = "",
        currentCity: String = "",
        currentProvince: String = "",
        phoneNumbers: List<String> = emptyList(),

        // Status
        status: String,
        riskLevel: String,
        gangAffiliation: String = "",
        isArmed: Boolean = false,
        isDangerous: Boolean = false,

        // Images
        imageBitmaps: List<Bitmap>,

        // Admin
        notes: String = ""
    ): Result<Criminal> {
        return try {
            val officerId = auth.currentUser?.uid
                ?: return Result.Error(Exception("Officer not logged in"))

            // Generate new criminal ID
            val docId = criminalsCollection.document().id
            val subjectId = "SUB-${System.currentTimeMillis()}-${docId.take(6).uppercase()}"

            val criminal = Criminal(
                id = docId,
                subjectId = subjectId,

                // Personal Info
                firstName = firstName,
                lastName = lastName,
                middleName = middleName,
                aliases = aliases,
                dateOfBirth = dateOfBirth,
                gender = gender,
                nationality = nationality,
                nationalId = nationalId,

                // Physical Description
                height = height,
                weight = weight,
                eyeColor = eyeColor,
                hairColor = hairColor,
                build = build,
                skinTone = skinTone,
                distinguishingMarks = distinguishingMarks,
                tattooDescriptions = tattooDescriptions,

                // Contact & Location
                lastKnownAddress = lastKnownAddress,
                currentCity = currentCity,
                currentProvince = currentProvince,
                phoneNumbers = phoneNumbers,

                // Status
                status = status,
                riskLevel = riskLevel,
                gangAffiliation = gangAffiliation,
                isArmed = isArmed,
                isDangerous = isDangerous,

                // Images count
                imageCount = imageBitmaps.size,

                // Admin
                createdByOfficerId = officerId,
                lastUpdatedByOfficerId = officerId,
                notes = notes,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            // Save criminal document first
            criminalsCollection.document(docId)
                .set(criminal)
                .await()

            // Save all images to subcollection
            imageBitmaps.forEachIndexed { index, bitmap ->
                addCriminalImageInternal(docId, bitmap, officerId, if (index == 0) "primary" else "additional")
            }

            Result.Success(criminal)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Add a single image to existing criminal
     */
    suspend fun addImageToCriminal(
        criminalId: String,
        imageBitmap: Bitmap
    ): Result<CriminalImage> {
        return try {
            val officerId = auth.currentUser?.uid
                ?: return Result.Error(Exception("Officer not logged in"))

            val image = addCriminalImageInternal(criminalId, imageBitmap, officerId, "additional")

            // Update criminal's image count
            val criminalDoc = criminalsCollection.document(criminalId).get().await()
            val currentCount = criminalDoc.getLong("image_count")?.toInt() ?: 0

            criminalsCollection.document(criminalId).update(
                mapOf(
                    "image_count" to currentCount + 1,
                    "updated_at" to System.currentTimeMillis()
                )
            ).await()

            Result.Success(image)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    private suspend fun addCriminalImageInternal(
        criminalId: String,
        bitmap: Bitmap,
        officerId: String,
        imageType: String
    ): CriminalImage {
        val imagesCollection = getCriminalImagesCollection(criminalId)

        // Compress image
        val compressedImage = ImageCompressor.compressBitmap(bitmap, maxWidth = 800, maxHeight = 800, quality = 70)
        val imageBase64 = ImageCompressor.bitmapToBase64(compressedImage, quality = 70)

        // Generate image document ID
        val imageDocId = imagesCollection.document().id

        val criminalImage = CriminalImage(
            id = imageDocId,
            criminalId = criminalId,
            imageBase64 = imageBase64,
            imageType = imageType,
            createdByOfficerId = officerId,
            createdAt = System.currentTimeMillis()
        )

        // Save to subcollection
        imagesCollection.document(imageDocId)
            .set(criminalImage)
            .await()

        return criminalImage
    }

    /**
     * Get all images for a criminal
     */
    suspend fun getCriminalImages(criminalId: String): Result<List<CriminalImage>> {
        return try {
            val collection = getCriminalImagesCollection(criminalId)

            val snapshot = collection
                .orderBy("created_at", Query.Direction.ASCENDING)
                .get()
                .await()

            val images = snapshot.documents.mapNotNull {
                it.toObject(CriminalImage::class.java)
            }

            Result.Success(images)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Get primary image for a criminal (first image)
     */
    suspend fun getCriminalPrimaryImage(criminalId: String): Result<CriminalImage?> {
        return try {
            val collection = getCriminalImagesCollection(criminalId)

            val snapshot = collection
                .whereEqualTo("image_type", "primary")
                .limit(1)
                .get()
                .await()

            val image = snapshot.documents.firstOrNull()?.toObject(CriminalImage::class.java)
                ?: getCriminalImages(criminalId).let { result ->
                    if (result is Result.Success) result.data.firstOrNull() else null
                }

            Result.Success(image)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getCriminalById(criminalId: String): Result<Criminal> {
        return try {
            val snapshot = criminalsCollection.document(criminalId)
                .get()
                .await()

            val criminal = snapshot.toObject(Criminal::class.java)
                ?: return Result.Error(Exception("Criminal not found"))

            // Log this access for audit
            logSearch(
                searchType = "ID_LOOKUP",
                searchParams = mapOf("criminal_id" to criminalId),
                resultsCount = 1,
                matchedSubjectIds = listOf(criminal.subjectId)
            )

            Result.Success(criminal)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun searchCriminalsByName(
        firstName: String = "",
        lastName: String = "",
        limit: Int = 50
    ): Result<List<Criminal>> {
        return try {
            var query: Query = criminalsCollection

            if (lastName.isNotEmpty()) {
                query = query.whereEqualTo("lastName", lastName)
            }

            if (firstName.isNotEmpty()) {
                query = query.whereEqualTo("firstName", firstName)
            }

            val snapshot = query.limit(limit.toLong()).get().await()
            val criminals = snapshot.documents.mapNotNull {
                it.toObject(Criminal::class.java)
            }

            // Log search
            logSearch(
                searchType = "NAME_SEARCH",
                searchParams = mapOf("firstName" to firstName, "lastName" to lastName),
                resultsCount = criminals.size,
                matchedSubjectIds = criminals.map { it.subjectId }
            )

            Result.Success(criminals)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun searchCriminalsByStatus(
        status: String,
        riskLevel: String? = null
    ): Result<List<Criminal>> {
        return try {
            var query: Query = criminalsCollection
                .whereEqualTo("status", status)

            if (riskLevel != null) {
                query = query.whereEqualTo("riskLevel", riskLevel)
            }

            val snapshot = query
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val criminals = snapshot.documents.mapNotNull {
                it.toObject(Criminal::class.java)
            }

            Result.Success(criminals)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getAllCriminals(limit: Int = 100): Result<List<Criminal>> {
        return try {
            val snapshot = criminalsCollection
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()

            val criminals = snapshot.documents.mapNotNull {
                it.toObject(Criminal::class.java)
            }

            Result.Success(criminals)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getWantedCriminals(): Result<List<Criminal>> {
        return searchCriminalsByStatus("Wanted")
    }

    suspend fun getHighRiskCriminals(): Result<List<Criminal>> {
        return try {
            val snapshot = criminalsCollection
                .whereIn("riskLevel", listOf("High", "Extreme"))
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val criminals = snapshot.documents.mapNotNull {
                it.toObject(Criminal::class.java)
            }

            Result.Success(criminals)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    // ============================================
    // CRIME MANAGEMENT
    // ============================================

    suspend fun addCrime(
        criminalId: String,
        subjectId: String,
        crimeType: String,
        crimeCategory: String,
        description: String,
        criminalCodeSection: String = "",
        severity: String, // Misdemeanor, Felony

        // Location & Time
        crimeDate: String,
        crimeTime: String = "",
        locationAddress: String,
        locationCity: String,
        locationProvince: String,

        // Victims
        victimNames: List<String> = emptyList(),
        victimCount: Int = 0,
        injuriesCaused: String = "None",

        // Investigation
        caseNumber: String = "",
        arrestDate: String = "",
        arrestLocation: String = "",
        status: String = "Under Investigation", // Under Investigation, Charged, Convicted

        notes: String = ""
    ): Result<Crime> {
        return try {
            val officerId = auth.currentUser?.uid
                ?: return Result.Error(Exception("Officer not logged in"))

            val docId = crimesCollection.document().id
            val crimeId = "CRIME-${System.currentTimeMillis()}-${docId.take(6).uppercase()}"

            val crime = Crime(
                id = docId,
                crimeId = crimeId,
                criminalId = criminalId,
                subjectId = subjectId,

                // Crime Details
                crimeType = crimeType,
                crimeCategory = crimeCategory,
                description = description,
                criminalCodeSection = criminalCodeSection,
                severity = severity,

                // Location & Time
                crimeDate = crimeDate,
                crimeTime = crimeTime,
                locationAddress = locationAddress,
                locationCity = locationCity,
                locationProvince = locationProvince,

                // Victims
                victimNames = victimNames,
                victimCount = victimCount,
                injuriesCaused = injuriesCaused,

                // Investigation
                caseNumber = caseNumber,
                arrestDate = arrestDate,
                arrestLocation = arrestLocation,
                investigatingOfficerId = officerId,
                status = status,

                notes = notes,
                createdByOfficerId = officerId,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            crimesCollection.document(docId)
                .set(crime)
                .await()

            Result.Success(crime)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getCrimesByCriminal(criminalId: String): Result<List<Crime>> {
        return try {
            val snapshot = crimesCollection
                .whereEqualTo("criminalId", criminalId)
                .orderBy("crimeDate", Query.Direction.DESCENDING)
                .get()
                .await()

            val crimes = snapshot.documents.mapNotNull {
                it.toObject(Crime::class.java)
            }

            Result.Success(crimes)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun updateCrime(crime: Crime): Result<Crime> {
        return try {
            val updatedCrime = crime.copy(updatedAt = System.currentTimeMillis())

            crimesCollection.document(crime.id)
                .set(updatedCrime)
                .await()

            Result.Success(updatedCrime)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    // ============================================
    // WARRANT MANAGEMENT
    // ============================================

    suspend fun addWarrant(
        criminalId: String,
        subjectId: String,
        crimeId: String,
        warrantType: String, // Arrest Warrant, Search Warrant, Bench Warrant
        warrantNumber: String,
        issueDate: String,
        issuingCourt: String,
        judgeName: String,
        bailAmount: Double = 0.0,
        notes: String = ""
    ): Result<Warrant> {
        return try {
            val officerId = auth.currentUser?.uid
                ?: return Result.Error(Exception("Officer not logged in"))

            val docId = warrantsCollection.document().id
            val warrantId = "WARRANT-${System.currentTimeMillis()}-${docId.take(6).uppercase()}"

            val warrant = Warrant(
                id = docId,
                warrantId = warrantId,
                criminalId = criminalId,
                subjectId = subjectId,
                crimeId = crimeId,
                warrantType = warrantType,
                warrantNumber = warrantNumber,
                issueDate = issueDate,
                issuingCourt = issuingCourt,
                judgeName = judgeName,
                warrantStatus = "Active",
                bailAmount = bailAmount,
                notes = notes,
                createdByOfficerId = officerId,
                createdAt = System.currentTimeMillis()
            )

            warrantsCollection.document(docId)
                .set(warrant)
                .await()

            // Update criminal status to "Wanted"
            val criminal = getCriminalById(criminalId)
            if (criminal is Result.Success) {
                updateCriminal(criminal.data.copy(status = "Wanted"))
            }

            Result.Success(warrant)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getWarrantsByCriminal(criminalId: String): Result<List<Warrant>> {
        return try {
            val snapshot = warrantsCollection
                .whereEqualTo("criminalId", criminalId)
                .orderBy("issueDate", Query.Direction.DESCENDING)
                .get()
                .await()

            val warrants = snapshot.documents.mapNotNull {
                it.toObject(Warrant::class.java)
            }

            Result.Success(warrants)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getActiveWarrants(): Result<List<Warrant>> {
        return try {
            val snapshot = warrantsCollection
                .whereEqualTo("warrantStatus", "Active")
                .orderBy("issueDate", Query.Direction.DESCENDING)
                .get()
                .await()

            val warrants = snapshot.documents.mapNotNull {
                it.toObject(Warrant::class.java)
            }

            Result.Success(warrants)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    // ============================================
    // SEARCH AUDIT LOGGING
    // ============================================

    private suspend fun logSearch(
        searchType: String,
        searchParams: Map<String, String>,
        resultsCount: Int,
        matchedSubjectIds: List<String> = emptyList(),
        searchReason: String = "",
        caseNumber: String = ""
    ) {
        try {
            val officerId = auth.currentUser?.uid ?: "UNKNOWN"
            val docId = searchAuditCollection.document().id

            val auditLog = hashMapOf(
                "search_id" to docId,
                "officer_id" to officerId,
                "timestamp" to System.currentTimeMillis(),
                "search_type" to searchType,
                "search_parameters" to searchParams,
                "results_count" to resultsCount,
                "matched_subject_ids" to matchedSubjectIds,
                "search_reason" to searchReason,
                "case_number" to caseNumber
            )

            searchAuditCollection.document(docId)
                .set(auditLog)
                .await()
        } catch (e: Exception) {
            // Log error but don't fail the main operation
            e.printStackTrace()
        }
    }

    suspend fun getSearchAuditLogs(
        officerId: String? = null,
        limit: Int = 50
    ): Result<List<Map<String, Any>>> {
        return try {
            var query: Query = searchAuditCollection
                .orderBy("timestamp", Query.Direction.DESCENDING)

            if (officerId != null) {
                query = query.whereEqualTo("officer_id", officerId)
            }

            val snapshot = query.limit(limit.toLong()).get().await()
            val logs = snapshot.documents.map { it.data ?: emptyMap() }

            Result.Success(logs)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    // ============================================
    // FACE RECOGNITION SEARCH
    // ============================================

    suspend fun searchByFaceFeatures(
        faceFeatures: Map<String, Float>,
        threshold: Float = 0.85f
    ): Result<List<Pair<Criminal, Float>>> {
        return try {
            // Get all criminals with face features
            val snapshot = criminalsCollection
                .whereNotEqualTo("faceFeatures", null)
                .get()
                .await()

            val criminals = snapshot.documents.mapNotNull {
                it.toObject(Criminal::class.java)
            }

            // Calculate similarity scores (cosine similarity)
            val matches = criminals.mapNotNull { criminal ->
                val storedFeatures = criminal.faceFeatures
                if (storedFeatures.isNotEmpty()) {
                    val similarity = calculateCosineSimilarity(faceFeatures, storedFeatures)
                    if (similarity >= threshold) {
                        Pair(criminal, similarity)
                    } else null
                } else null
            }.sortedByDescending { it.second }

            // Log face recognition search
            logSearch(
                searchType = "FACE_RECOGNITION",
                searchParams = mapOf("threshold" to threshold.toString()),
                resultsCount = matches.size,
                matchedSubjectIds = matches.map { it.first.subjectId }
            )

            Result.Success(matches)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    private fun calculateCosineSimilarity(
        features1: Map<String, Float>,
        features2: Map<String, Float>
    ): Float {
        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f

        features1.forEach { (key, value1) ->
            val value2 = features2[key] ?: 0f
            dotProduct += value1 * value2
            norm1 += value1 * value1
            norm2 += value2 * value2
        }

        return if (norm1 == 0f || norm2 == 0f) 0f
        else dotProduct / (kotlin.math.sqrt(norm1) * kotlin.math.sqrt(norm2))
    }
}