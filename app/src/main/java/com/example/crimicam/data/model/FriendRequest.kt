package com.example.crimicam.data.model

data class FriendRequest(
    val id: String = "",
    val fromUserId: String = "",
    val toUserId: String = "",
    val fromUserName: String = "",
    val toUserName: String = "",
    val status: String = "", // pending, accepted, declined
    val createdAt: Long = 0
) {
    // Empty constructor for Firestore
    constructor() : this("", "", "", "", "", "", 0)
}