package com.tomergee.mp3player.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PlaylistDao {

    @Query(
        """
        SELECT p.id, p.name, COUNT(pt.track_id) AS track_count
        FROM playlists p LEFT JOIN playlist_tracks pt ON pt.playlist_id = p.id
        GROUP BY p.id ORDER BY p.name COLLATE NOCASE
        """,
    )
    abstract fun observeSummaries(): Flow<List<PlaylistSummary>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    abstract fun observePlaylist(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT track_id FROM playlist_tracks WHERE playlist_id = :playlistId ORDER BY position")
    abstract fun observeTrackIds(playlistId: Long): Flow<List<Long>>

    @Query("SELECT track_id FROM playlist_tracks WHERE playlist_id = :playlistId ORDER BY position")
    abstract suspend fun getTrackIds(playlistId: Long): List<Long>

    @Query("SELECT DISTINCT track_id FROM playlist_tracks")
    abstract suspend fun getAllReferencedTrackIds(): List<Long>

    @Insert
    abstract suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    abstract suspend fun renamePlaylist(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    abstract suspend fun deletePlaylist(id: Long)

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM playlist_tracks WHERE playlist_id = :playlistId")
    abstract suspend fun nextPosition(playlistId: Long): Int

    /** Returns -1 when the track is already in the playlist. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertTrack(entry: PlaylistTrackEntity): Long

    @Query("DELETE FROM playlist_tracks WHERE playlist_id = :playlistId AND track_id = :trackId")
    abstract suspend fun deleteTrack(playlistId: Long, trackId: Long)

    @Query("DELETE FROM playlist_tracks WHERE track_id IN (:trackIds)")
    abstract suspend fun deleteTracksEverywhere(trackIds: List<Long>)

    @Query("DELETE FROM playlist_tracks WHERE playlist_id = :playlistId")
    abstract suspend fun clearPlaylist(playlistId: Long)

    @Insert
    abstract suspend fun insertTracks(entries: List<PlaylistTrackEntity>)

    @Query("SELECT DISTINCT playlist_id FROM playlist_tracks WHERE track_id IN (:trackIds)")
    abstract suspend fun getPlaylistIdsContaining(trackIds: List<Long>): List<Long>

    /** Appends a track at the end; no-op if it is already present. */
    @Transaction
    open suspend fun appendTrack(playlistId: Long, trackId: Long): Boolean =
        insertTrack(PlaylistTrackEntity(playlistId, trackId, nextPosition(playlistId))) != -1L

    /** Atomically replaces the playlist's contents with [orderedTrackIds], positions 0..n-1. */
    @Transaction
    open suspend fun rewriteOrder(playlistId: Long, orderedTrackIds: List<Long>) {
        clearPlaylist(playlistId)
        insertTracks(orderedTrackIds.mapIndexed { i, id -> PlaylistTrackEntity(playlistId, id, i) })
    }

    @Transaction
    open suspend fun removeTrackAndCompact(playlistId: Long, trackId: Long) {
        deleteTrack(playlistId, trackId)
        rewriteOrder(playlistId, getTrackIds(playlistId))
    }

    /**
     * Moves [trackId] into the slot currently held by [targetTrackId]. Keyed by id rather than
     * index so a stale or filtered UI list can never move the wrong row.
     */
    @Transaction
    open suspend fun moveTrack(playlistId: Long, trackId: Long, targetTrackId: Long) {
        val ids = getTrackIds(playlistId).toMutableList()
        val from = ids.indexOf(trackId)
        val to = ids.indexOf(targetTrackId)
        if (from < 0 || to < 0 || from == to) return
        ids.add(to, ids.removeAt(from))
        rewriteOrder(playlistId, ids)
    }

    /** Deletes references to [missingTrackIds] (chunked under SQLite's 999-variable limit) and re-densifies positions. */
    @Transaction
    open suspend fun pruneTracks(missingTrackIds: List<Long>) {
        if (missingTrackIds.isEmpty()) return
        val affected = mutableSetOf<Long>()
        missingTrackIds.chunked(MAX_SQL_VARIABLES).forEach { chunk ->
            affected += getPlaylistIdsContaining(chunk)
            deleteTracksEverywhere(chunk)
        }
        affected.forEach { rewriteOrder(it, getTrackIds(it)) }
    }

    private companion object {
        const val MAX_SQL_VARIABLES = 500
    }
}
