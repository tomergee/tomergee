package com.tomergee.mp3player.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tomergee.mp3player.Mp3PlayerApp
import com.tomergee.mp3player.data.PlaylistRepository
import com.tomergee.mp3player.data.Track
import com.tomergee.mp3player.data.TrackRepository
import com.tomergee.mp3player.data.db.PlaylistSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val hasPermission: Boolean = false,
    val isLoading: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val error: String? = null,
)

data class PlaylistDetail(val id: Long, val name: String, val tracks: List<Track>)

class LibraryViewModel(
    private val trackRepository: TrackRepository,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    val playlists: StateFlow<List<PlaylistSummary>> = playlistRepository.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = Channel<String>(Channel.BUFFERED)
    /** One-shot user feedback (snackbars). */
    val messages: Flow<String> = _messages.receiveAsFlow()

    private var scanJob: Job? = null

    fun onPermissionResult(granted: Boolean) {
        val wasGranted = _state.value.hasPermission
        _state.update { it.copy(hasPermission = granted) }
        if (granted && !wasGranted) refresh()
    }

    /** Rescans storage; a newer call cancels an in-flight scan. */
    fun refresh() {
        if (!_state.value.hasPermission) return
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val tracks = trackRepository.loadTracks()
                _state.update { it.copy(isLoading = false, tracks = tracks) }
                // An empty result is more likely an unmounted volume than "user deleted every
                // song"; never wipe playlists on it.
                if (tracks.isNotEmpty()) playlistRepository.pruneMissing(tracks.mapTo(HashSet()) { it.id })
            } catch (e: SecurityException) {
                _state.update { it.copy(isLoading = false, hasPermission = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Couldn't read music: ${e.message}") }
            }
        }
    }

    /** Playlist contents joined with the current library; tracks missing from storage are hidden. */
    fun observePlaylistDetail(playlistId: Long): Flow<PlaylistDetail?> = combine(
        playlistRepository.observePlaylist(playlistId),
        playlistRepository.observeTrackIds(playlistId),
        _state,
    ) { playlist, ids, library ->
        playlist ?: return@combine null
        val byId = library.tracks.associateBy(Track::id)
        PlaylistDetail(playlist.id, playlist.name, ids.mapNotNull(byId::get))
    }

    fun createPlaylist(name: String, thenAddTrackId: Long? = null) = launchReporting {
        val id = playlistRepository.create(name)
        if (thenAddTrackId != null) playlistRepository.addTrack(id, thenAddTrackId)
        _messages.send("Created \"${name.trim()}\"")
    }

    fun renamePlaylist(id: Long, name: String) = launchReporting { playlistRepository.rename(id, name) }

    fun deletePlaylist(id: Long) = launchReporting { playlistRepository.delete(id) }

    fun addToPlaylist(playlist: PlaylistSummary, trackId: Long) = launchReporting {
        val added = playlistRepository.addTrack(playlist.id, trackId)
        _messages.send(if (added) "Added to ${playlist.name}" else "Already in ${playlist.name}")
    }

    fun removeFromPlaylist(playlistId: Long, trackId: Long) =
        launchReporting { playlistRepository.removeTrack(playlistId, trackId) }

    fun moveInPlaylist(playlistId: Long, trackId: Long, targetTrackId: Long) =
        launchReporting { playlistRepository.moveTrack(playlistId, trackId, targetTrackId) }

    private fun launchReporting(block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
        } catch (e: IllegalArgumentException) {
            _messages.send(e.message ?: "Invalid input")
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as Mp3PlayerApp).container
                LibraryViewModel(container.trackRepository, container.playlistRepository)
            }
        }
    }
}
