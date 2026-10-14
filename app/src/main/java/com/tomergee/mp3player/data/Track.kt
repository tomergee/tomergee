package com.tomergee.mp3player.data

/** An MP3 on the device, identified by its MediaStore audio `_ID`. */
data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
)
