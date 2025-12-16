
package com.example.crimicam.data.model

import com.google.firebase.firestore.DocumentId

/**
 * Main person document
 * Location: users/{userId}/known_people/{personId}
 */
data class KnownPerson(
    @DocumentId
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val description: String = "",
    val imageCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
