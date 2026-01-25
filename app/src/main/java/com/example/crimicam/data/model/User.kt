package com.example.crimicam.data.model

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val friendCode: String = "",
    val friends: List<String> = emptyList(),
    val friendRequests: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
) {
    // Empty constructor for Firestore
    constructor() : this("", "", "", "", emptyList(), emptyList(), 0)
}
