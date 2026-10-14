package com.tomergee.mp3player

import com.tomergee.mp3player.data.Track
import com.tomergee.mp3player.data.TrackRepository
import com.tomergee.mp3player.player.AudioPlayer
import com.tomergee.mp3player.player.PlayerState
import com.tomergee.mp3player.player.RepeatMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

fun track(id: Long, title: String = "Song $id") =
    Track(id = id, title = title, artist = "Artist", album = "Album", durationMs = 180_000)

class FakeTrackRepository(var tracks: List<Track> = emptyList()) : TrackRepository {
    var calls = 0
    var error: Exception? = null

    override suspend fun loadTracks(): List<Track> {
        calls++
        error?.let { throw it }
        return tracks
    }
}

class FakeAudioPlayer : AudioPlayer {
    override val state = MutableStateFlow(PlayerState())
    var queue: List<Track> = emptyList()
    var released = false
    var lastSeek: Long? = null
    var nextCalls = 0
    var previousCalls = 0

    override fun playQueue(tracks: List<Track>, startIndex: Int) {
        queue = tracks
        state.update { it.copy(currentTrack = tracks[startIndex], isPlaying = true) }
    }
    override fun play() = state.update { it.copy(isPlaying = true) }
    override fun pause() = state.update { it.copy(isPlaying = false) }
    override fun seekTo(positionMs: Long) { lastSeek = positionMs }
    override fun next() { nextCalls++ }
    override fun previous() { previousCalls++ }
    override fun setShuffle(enabled: Boolean) = state.update { it.copy(shuffle = enabled) }
    override fun setRepeatMode(mode: RepeatMode) = state.update { it.copy(repeatMode = mode) }
    override fun release() { released = true }
}
