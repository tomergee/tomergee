package com.tomergee.mp3player.data

import com.tomergee.mp3player.data.db.PlaylistDao
import com.tomergee.mp3player.data.db.PlaylistEntity
import com.tomergee.mp3player.data.db.PlaylistSummary
import kotlinx.coroutines.flow.Flow

class PlaylistRepository(
    private val dao: PlaylistDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observePlaylists(): Flow<List<PlaylistSummary>> = dao.observeSummaries()

    fun observePlaylist(id: Long): Flow<PlaylistEntity?> = dao.observePlaylist(id)

    fun observeTrackIds(playlistId: Long): Flow<List<Long>> = dao.observeTrackIds(playlistId)

    suspend fun create(name: String): Long =
        dao.insertPlaylist(PlaylistEntity(name = validName(name), createdAt = clock()))

    suspend fun rename(id: Long, name: String) = dao.renamePlaylist(id, validName(name))

    suspend fun delete(id: Long) = dao.deletePlaylist(id)

    /** @return false if the track was already in the playlist. */
    suspend fun addTrack(playlistId: Long, trackId: Long): Boolean = dao.appendTrack(playlistId, trackId)

    suspend fun removeTrack(playlistId: Long, trackId: Long) = dao.removeTrackAndCompact(playlistId, trackId)

    suspend fun moveTrack(playlistId: Long, trackId: Long, targetTrackId: Long) =
        dao.moveTrack(playlistId, trackId, targetTrackId)

    /** Removes playlist entries whose track no longer exists on the device. */
    suspend fun pruneMissing(existingTrackIds: Set<Long>) {
        val missing = dao.getAllReferencedTrackIds().filterNot(existingTrackIds::contains)
        dao.pruneTracks(missing)
    }

    private fun validName(name: String): String {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Playlist name must not be blank" }
        require(trimmed.length <= MAX_NAME_LENGTH) { "Playlist name too long" }
        return trimmed
    }

    companion object {
        const val MAX_NAME_LENGTH = 100
    }
}
