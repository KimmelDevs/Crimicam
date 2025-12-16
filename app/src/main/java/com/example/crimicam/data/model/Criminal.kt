package com.example.crimicam.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude

data class Criminal(
    val id: String = "",
    val subjectId: String = "", // Unique identifier like "SUB-12345"

    // Personal Information
    val firstName: String = "",
    val lastName: String = "",
    val middleName: String = "",
    val aliases: List<String> = emptyList(),
    val dateOfBirth: String = "",
    val gender: String = "",
    val nationality: String = "",
    val nationalId: String = "",

    // Physical Description
    val height: Int = 0,
    val weight: Int = 0,
    val eyeColor: String = "",
    val hairColor: String = "",
    val build: String = "",
    val skinTone: String = "",
    val distinguishingMarks: String = "",
    val tattooDescriptions: List<String> = emptyList(),

    // Contact & Location
    val lastKnownAddress: String = "",
    val currentCity: String = "",
    val currentProvince: String = "",
    val phoneNumbers: List<String> = emptyList(),

    // Status
    val status: String = "", // Active, Arrested, Wanted, Released, Deceased
    val riskLevel: String = "", // Low, Medium, High, Extreme
    val gangAffiliation: String = "",
    val isArmed: Boolean = false,
    val isDangerous: Boolean = false,

    // Images & Biometrics (for legacy support - prefer using CriminalImagesVectorDB)
    val mugshotBase64: String? = null,
    val croppedFaceBase64: String? = null,
    val faceFeatures: Map<String, Float> = emptyMap(),
    val imageCount: Int = 0,

    // Admin metadata
    val createdByOfficerId: String = "",
    val lastUpdatedByOfficerId: String = "",
    val notes: String = "",
    val createdAt: Timestamp? = null,
    val lastUpdated: Timestamp? = null,

    // Legacy support for Long timestamps (will be converted)
    @Deprecated("Use createdAt with Timestamp")
    val createdAtLong: Long? = null,
    @Deprecated("Use lastUpdated with Timestamp")
    val updatedAt: Long? = null
) {
    @get:Exclude
    val fullName: String
        get() = listOf(firstName, middleName, lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")

    @get:Exclude
    val age: Int
        get() = calculateAge()

    private fun calculateAge(): Int {
        return try {
            val birthYear = dateOfBirth.substring(0, 4).toInt()
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            currentYear - birthYear
        } catch (e: Exception) {
            0
        }
    }

    @get:Exclude
    val displayStatus: String
        get() = when {
            status == "Wanted" && riskLevel in listOf("High", "Extreme") -> "⚠️ WANTED - HIGH RISK"
            status == "Wanted" -> "⚠️ WANTED"
            isDangerous -> "⚠️ DANGEROUS"
            isArmed -> "⚠️ ARMED"
            else -> status
        }
}

data class Crime(
    val id: String = "",
    val crimeId: String = "",
    val criminalId: String = "",
    val subjectId: String = "",

    // Crime Details
    val crimeType: String = "",
    val crimeCategory: String = "",
    val description: String = "",
    val criminalCodeSection: String = "",
    val severity: String = "", // Misdemeanor, Felony

    // Location & Time
    val crimeDate: String = "",
    val crimeTime: String = "",
    val locationAddress: String = "",
    val locationCity: String = "",
    val locationProvince: String = "",

    // Victims
    val victimNames: List<String> = emptyList(),
    val victimCount: Int = 0,
    val injuriesCaused: String = "None",

    // Investigation
    val caseNumber: String = "",
    val arrestDate: String = "",
    val arrestLocation: String = "",
    val investigatingOfficerId: String = "",
    val status: String = "Under Investigation", // Under Investigation, Charged, Convicted

    // Admin
    val notes: String = "",
    val createdByOfficerId: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

data class CriminalImage(
    val id: String = "",
    val criminalId: String = "",
    val imageBase64: String = "",
    val imageType: String = "", // primary, additional, mugshot
    val capturedDate: String = "",
    val captureLocation: String = "",
    val createdByOfficerId: String = "",
    val createdAt: Timestamp? = null
)

data class Warrant(
    val id: String = "",
    val warrantId: String = "",
    val criminalId: String = "",
    val subjectId: String = "",
    val crimeId: String = "",

    // Warrant Details
    val warrantType: String = "", // Arrest Warrant, Search Warrant, Bench Warrant
    val warrantNumber: String = "",
    val issueDate: String = "",
    val issuingCourt: String = "",
    val judgeName: String = "",
    val warrantStatus: String = "Active", // Active, Executed, Cancelled
    val bailAmount: Double = 0.0,

    // Execution
    val executionDate: String = "",
    val executingOfficerId: String = "",

    // Admin
    val notes: String = "",
    val createdByOfficerId: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)