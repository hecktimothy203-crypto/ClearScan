package com.timclearscan.app

data class Feed(
    val id: String,
    val name: String,
    val location: String,
    val url: String,
    val favorite: Boolean = false,
    val lastPlayedAt: Long = 0L
)
