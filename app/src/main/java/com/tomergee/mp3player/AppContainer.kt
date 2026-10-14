package com.tomergee.mp3player

import android.content.Context
import com.tomergee.mp3player.data.MediaStoreTrackRepository
import com.tomergee.mp3player.data.PlaylistRepository
import com.tomergee.mp3player.data.TrackRepository
import com.tomergee.mp3player.data.db.AppDatabase
import com.tomergee.mp3player.player.AudioPlayer
import com.tomergee.mp3player.player.ExoAudioPlayer

/** Manual dependency container; one instance per process. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database by lazy { AppDatabase.create(appContext) }

    val trackRepository: TrackRepository by lazy { MediaStoreTrackRepository(appContext.contentResolver) }
    val playlistRepository by lazy { PlaylistRepository(database.playlistDao()) }

    fun createPlayer(): AudioPlayer = ExoAudioPlayer(appContext)
}
