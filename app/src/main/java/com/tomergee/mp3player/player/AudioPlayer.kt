package com.tomergee.mp3player.player

import com.tomergee.mp3player.data.Track
import kotlinx.coroutines.flow.StateFlow

enum class RepeatMode {
    OFF, ALL, ONE;

    fun next(): RepeatMode = entries[(ordinal + 1) % entries.size]
}

data class PlayerState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
)

/** Playback abstraction so view models can be tested without ExoPlayer. */
interface AudioPlayer {
    val state: StateFlow<PlayerState>

    /** Replaces the queue with a snapshot of [tracks] and starts playing at [startIndex]. */
    fun playQueue(tracks: List<Track>, startIndex: Int)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun next()
    fun previous()
    fun setShuffle(enabled: Boolean)
    fun setRepeatMode(mode: RepeatMode)
    fun release()
}
