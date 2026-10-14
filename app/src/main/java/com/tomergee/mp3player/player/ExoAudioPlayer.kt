package com.tomergee.mp3player.player

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.tomergee.mp3player.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** ExoPlayer-backed [AudioPlayer]. Must be used from the main thread. */
class ExoAudioPlayer(context: Context) : AudioPlayer {

    private val player = ExoPlayer.Builder(context.applicationContext)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var progressJob: Job? = null
    private var tracksById: Map<String, Track> = emptyMap()

    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    init {
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = publishState()

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) startProgressUpdates() else progressJob?.cancel()
            }
        })
    }

    override fun playQueue(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        tracksById = tracks.associateBy { it.id.toString() }
        val items = tracks.map { track ->
            MediaItem.Builder()
                .setMediaId(track.id.toString())
                .setUri(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, track.id))
                .build()
        }
        player.setMediaItems(items, startIndex.coerceIn(tracks.indices), 0L)
        player.prepare()
        player.play()
    }

    override fun play() {
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0, 0L)
        player.play()
    }

    override fun pause() = player.pause()

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        publishState()
    }

    /** Standard behaviour: previous restarts the track if past the first few seconds. */
    override fun previous() = player.seekToPrevious()

    override fun next() = player.seekToNext()

    override fun setShuffle(enabled: Boolean) {
        player.shuffleModeEnabled = enabled
    }

    override fun setRepeatMode(mode: RepeatMode) {
        player.repeatMode = when (mode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
    }

    override fun release() {
        scope.cancel()
        player.release()
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                publishState()
                delay(PROGRESS_INTERVAL_MS)
            }
        }
    }

    private fun publishState() {
        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        _state.value = PlayerState(
            currentTrack = player.currentMediaItem?.mediaId?.let(tracksById::get),
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = duration,
            shuffle = player.shuffleModeEnabled,
            repeatMode = when (player.repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                else -> RepeatMode.OFF
            },
            hasNext = player.hasNextMediaItem(),
            hasPrevious = player.currentMediaItem != null,
        )
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 500L
    }
}
