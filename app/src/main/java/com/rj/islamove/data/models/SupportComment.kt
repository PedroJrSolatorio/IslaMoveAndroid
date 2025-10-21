package com.rj.islamove.data.models

data class SupportComment(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val message: String = "",
    val timestamp: Long = 0L
) {
    // No-arg constructor for Firebase
    constructor() : this("", "", "", "", 0L)
}