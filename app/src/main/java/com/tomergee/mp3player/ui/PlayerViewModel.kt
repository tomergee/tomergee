package com.tomergee.mp3player.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tomergee.mp3player.Mp3PlayerApp
import com.tomergee.mp3player.data.Track
import com.tomergee.mp3player.player.AudioPlayer
import com.tomergee.mp3player.player.PlayerState
import kotlinx.coroutines.flow.StateFlow

/** Owns the player so playback survives configuration changes; released in [onCleared]. */
class PlayerViewModel(private val player: AudioPlayer) : ViewModel() {

    val state: StateFlow<PlayerState> = player.state

    fun playTracks(tracks: List<Track>, startIndex: Int) = player.playQueue(tracks, startIndex)

    fun togglePlayPause() = if (state.value.isPlaying) player.pause() else player.play()

    fun next() = player.next()

    fun previous() = player.previous()

    fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    fun toggleShuffle() = player.setShuffle(!state.value.shuffle)

    fun cycleRepeatMode() = player.setRepeatMode(state.value.repeatMode.next())

    /** Foreground-only app: pause (keeping position) when the UI is no longer visible. */
    fun onAppBackgrounded() = player.pause()

    override fun onCleared() = player.release()

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { PlayerViewModel((this[APPLICATION_KEY] as Mp3PlayerApp).container.createPlayer()) }
        }
    }
}
