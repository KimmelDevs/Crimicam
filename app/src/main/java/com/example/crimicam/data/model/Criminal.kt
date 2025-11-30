// In your Criminal data class
package com.example.crimicam.data.model

import com.google.firebase.Timestamp

data class Criminal(
    val id: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val middleName: String = "",
    val dateOfBirth: String = "",
    val gender: String = "",
    val nationality: String = "",
    val nationalId: String = "",
    val height: Int = 0,
    val weight: Int = 0,
    val eyeColor: String = "",
    val hairColor: String = "",
    val build: String = "",
    val skinTone: String = "",
    val lastKnownAddress: String = "",
    val currentCity: String = "",
    val currentProvince: String = "",
    val status: String = "",
    val riskLevel: String = "",
    val isArmed: Boolean = false,
    val isDangerous: Boolean = false,
    val notes: String = "",
    val imageCount: Int = 0,
    val createdAt: Timestamp? = null,
    val lastUpdated: Timestamp? = null
) {
    // Add computed properties
    val fullName: String
        get() = listOf(firstName, middleName, lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")

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
}