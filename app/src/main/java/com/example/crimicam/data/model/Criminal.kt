package com.example.crimicam.data.model

import com.google.firebase.firestore.PropertyName

// ============================================
// CRIMINAL MODEL
// ============================================
data class Criminal(
    @PropertyName("id") val id: String = "",
    @PropertyName("subject_id") val subjectId: String = "",

    // Personal Information
    @PropertyName("first_name") val firstName: String = "",
    @PropertyName("last_name") val lastName: String = "",
    @PropertyName("middle_name") val middleName: String = "",
    @PropertyName("aliases") val aliases: List<String> = emptyList(),
    @PropertyName("date_of_birth") val dateOfBirth: String = "",
    @PropertyName("gender") val gender: String = "",
    @PropertyName("nationality") val nationality: String = "",
    @PropertyName("national_id") val nationalId: String = "",
    @PropertyName("passport_number") val passportNumber: String = "",
    @PropertyName("driver_license") val driverLicense: String = "",

    // Physical Description
    @PropertyName("height") val height: Int = 0, // in cm
    @PropertyName("weight") val weight: Int = 0, // in kg
    @PropertyName("eye_color") val eyeColor: String = "",
    @PropertyName("hair_color") val hairColor: String = "",
    @PropertyName("build") val build: String = "", // Slim, Medium, Heavy, Athletic
    @PropertyName("skin_tone") val skinTone: String = "",
    @PropertyName("distinguishing_marks") val distinguishingMarks: String = "",
    @PropertyName("tattoo_descriptions") val tattooDescriptions: List<String> = emptyList(),
    @PropertyName("physical_disabilities") val physicalDisabilities: String = "",

    // Contact & Location
    @PropertyName("last_known_address") val lastKnownAddress: String = "",
    @PropertyName("previous_addresses") val previousAddresses: List<String> = emptyList(),
    @PropertyName("current_city") val currentCity: String = "",
    @PropertyName("current_province") val currentProvince: String = "",
    @PropertyName("phone_numbers") val phoneNumbers: List<String> = emptyList(),
    @PropertyName("email_addresses") val emailAddresses: List<String> = emptyList(),

    // Status Information
    @PropertyName("status") val status: String = "", // Active, Arrested, Wanted, Released, Deceased
    @PropertyName("risk_level") val riskLevel: String = "", // Low, Medium, High, Extreme
    @PropertyName("gang_affiliation") val gangAffiliation: String = "",
    @PropertyName("known_associates") val knownAssociates: List<String> = emptyList(), // List of subject_ids
    @PropertyName("is_armed") val isArmed: Boolean = false,
    @PropertyName("is_dangerous") val isDangerous: Boolean = false,
    @PropertyName("escape_risk") val escapeRisk: String = "Low", // Low, Medium, High

    // Images & Biometrics
    @PropertyName("mugshot_base64") val mugshotBase64: String? = null,
    @PropertyName("cropped_face_base64") val croppedFaceBase64: String = "",
    @PropertyName("face_features") val faceFeatures: Map<String, Float> = emptyMap(),
    @PropertyName("fingerprint_id") val fingerprintId: String = "",

    // Administrative
    @PropertyName("created_by_officer_id") val createdByOfficerId: String = "",
    @PropertyName("last_updated_by_officer_id") val lastUpdatedByOfficerId: String = "",
    @PropertyName("data_source") val dataSource: String = "Manual Entry",
    @PropertyName("notes") val notes: String = "",
    @PropertyName("created_at") val createdAt: Long = System.currentTimeMillis(),
    @PropertyName("updated_at") val updatedAt: Long = System.currentTimeMillis()
) {
    // Helper properties
    val fullName: String
        get() = "$firstName $middleName $lastName".trim().replace("  ", " ")

    val age: Int
        get() {
            if (dateOfBirth.isEmpty()) return 0
            try {
                val birthYear = dateOfBirth.split("-")[0].toInt()
                val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                return currentYear - birthYear
            } catch (e: Exception) {
                return 0
            }
        }

    val isHighRisk: Boolean
        get() = riskLevel in listOf("High", "Extreme")

    val hasActiveWarrant: Boolean
        get() = status == "Wanted"
}

// ============================================
// CRIME MODEL
// ============================================
data class Crime(
    @PropertyName("id") val id: String = "",
    @PropertyName("crime_id") val crimeId: String = "",
    @PropertyName("criminal_id") val criminalId: String = "",
    @PropertyName("subject_id") val subjectId: String = "",
    @PropertyName("case_number") val caseNumber: String = "",

    // Crime Details
    @PropertyName("crime_type") val crimeType: String = "", // Theft, Assault, Murder, Drug Offense, Robbery
    @PropertyName("crime_category") val crimeCategory: String = "", // Violent, Property, Drug, White Collar
    @PropertyName("description") val description: String = "",
    @PropertyName("criminal_code_section") val criminalCodeSection: String = "",
    @PropertyName("severity") val severity: String = "", // Misdemeanor, Felony
    @PropertyName("modus_operandi") val modusOperandi: String = "",

    // Location & Time
    @PropertyName("crime_date") val crimeDate: String = "",
    @PropertyName("crime_time") val crimeTime: String = "",
    @PropertyName("location_address") val locationAddress: String = "",
    @PropertyName("location_city") val locationCity: String = "",
    @PropertyName("location_province") val locationProvince: String = "",
    @PropertyName("location_coordinates") val locationCoordinates: String = "", // "lat,lng"

    // Victims
    @PropertyName("victim_names") val victimNames: List<String> = emptyList(),
    @PropertyName("victim_count") val victimCount: Int = 0,
    @PropertyName("injuries_caused") val injuriesCaused: String = "None", // None, Minor, Serious, Fatal
    @PropertyName("property_stolen") val propertyStolen: String = "",
    @PropertyName("estimated_value") val estimatedValue: Double = 0.0,

    // Investigation
    @PropertyName("status") val status: String = "Under Investigation",
    @PropertyName("arrest_date") val arrestDate: String = "",
    @PropertyName("arrest_location") val arrestLocation: String = "",
    @PropertyName("arresting_officer_id") val arrestingOfficerId: String = "",
    @PropertyName("investigating_officer_id") val investigatingOfficerId: String = "",
    @PropertyName("case_status") val caseStatus: String = "Open", // Open, Closed, Cold Case

    // Legal Outcome
    @PropertyName("charge_filed_date") val chargeFiledDate: String = "",
    @PropertyName("court_case_number") val courtCaseNumber: String = "",
    @PropertyName("plea") val plea: String = "", // Guilty, Not Guilty, No Contest
    @PropertyName("conviction_date") val convictionDate: String = "",
    @PropertyName("sentence") val sentence: String = "",
    @PropertyName("sentence_length_months") val sentenceLengthMonths: Int = 0,
    @PropertyName("fine_amount") val fineAmount: Double = 0.0,

    // Evidence
    @PropertyName("evidence_collected") val evidenceCollected: List<String> = emptyList(),
    @PropertyName("weapon_used") val weaponUsed: String = "",
    @PropertyName("vehicle_involved") val vehicleInvolved: String = "",

    // Administrative
    @PropertyName("created_by_officer_id") val createdByOfficerId: String = "",
    @PropertyName("notes") val notes: String = "",
    @PropertyName("created_at") val createdAt: Long = System.currentTimeMillis(),
    @PropertyName("updated_at") val updatedAt: Long = System.currentTimeMillis()
) {
    val isViolentCrime: Boolean
        get() = crimeCategory == "Violent" || injuriesCaused in listOf("Serious", "Fatal")

    val isSolved: Boolean
        get() = caseStatus == "Closed" && status in listOf("Convicted", "Charged")
}

// ============================================
// WARRANT MODEL
// ============================================
data class Warrant(
    @PropertyName("id") val id: String = "",
    @PropertyName("warrant_id") val warrantId: String = "",
    @PropertyName("criminal_id") val criminalId: String = "",
    @PropertyName("subject_id") val subjectId: String = "",
    @PropertyName("crime_id") val crimeId: String = "",

    @PropertyName("warrant_type") val warrantType: String = "", // Arrest Warrant, Search Warrant, Bench Warrant
    @PropertyName("warrant_number") val warrantNumber: String = "",
    @PropertyName("issue_date") val issueDate: String = "",
    @PropertyName("expiry_date") val expiryDate: String = "",
    @PropertyName("issuing_court") val issuingCourt: String = "",
    @PropertyName("judge_name") val judgeName: String = "",

    @PropertyName("warrant_status") val warrantStatus: String = "Active", // Active, Executed, Expired, Recalled
    @PropertyName("bail_amount") val bailAmount: Double = 0.0,
    @PropertyName("execution_date") val executionDate: String = "",
    @PropertyName("executed_by_officer_id") val executedByOfficerId: String = "",

    @PropertyName("notes") val notes: String = "",
    @PropertyName("created_by_officer_id") val createdByOfficerId: String = "",
    @PropertyName("created_at") val createdAt: Long = System.currentTimeMillis()
) {
    val isActive: Boolean
        get() = warrantStatus == "Active"

    val isArrestWarrant: Boolean
        get() = warrantType == "Arrest Warrant"
}

// ============================================
// VEHICLE MODEL
// ============================================
data class Vehicle(
    @PropertyName("id") val id: String = "",
    @PropertyName("vehicle_id") val vehicleId: String = "",
    @PropertyName("criminal_id") val criminalId: String = "",
    @PropertyName("subject_id") val subjectId: String = "",

    @PropertyName("license_plate") val licensePlate: String = "",
    @PropertyName("make") val make: String = "",
    @PropertyName("model") val model: String = "",
    @PropertyName("year") val year: Int = 0,
    @PropertyName("color") val color: String = "",
    @PropertyName("vin_number") val vinNumber: String = "",

    @PropertyName("ownership_status") val ownershipStatus: String = "", // Owned, Stolen, Borrowed, Rental
    @PropertyName("last_seen_date") val lastSeenDate: String = "",
    @PropertyName("last_seen_location") val lastSeenLocation: String = "",

    @PropertyName("notes") val notes: String = "",
    @PropertyName("created_at") val createdAt: Long = System.currentTimeMillis()
)

// ============================================
// SEARCH RESULT MODEL (for face recognition)
// ============================================
data class CriminalMatch(
    val criminal: Criminal,
    val confidence: Float, // 0.0 to 1.0
    val matchType: String = "Face Recognition"
) {
    val isHighConfidence: Boolean
        get() = confidence >= 0.85f

    val confidencePercentage: Int
        get() = (confidence * 100).toInt()
}