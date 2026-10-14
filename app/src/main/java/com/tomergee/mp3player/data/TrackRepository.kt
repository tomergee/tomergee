package com.tomergee.mp3player.data

import android.content.ContentResolver
import android.database.Cursor
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface TrackRepository {
    /** Scans device storage. Must be called only once audio read permission is granted. */
    suspend fun loadTracks(): List<Track>
}

class MediaStoreTrackRepository(
    private val contentResolver: ContentResolver,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TrackRepository {

    override suspend fun loadTracks(): List<Track> = withContext(ioDispatcher) {
        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            PROJECTION,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.MIME_TYPE} = ?",
            arrayOf("audio/mpeg"),
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
        )?.use(::mapTracks).orEmpty()
    }

    companion object {
        val PROJECTION = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
        )

        private const val UNKNOWN = "<unknown>"

        /** Maps a cursor with [PROJECTION] columns to tracks. Column indices are resolved once. */
        fun mapTracks(cursor: Cursor): List<Track> {
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val tracks = ArrayList<Track>(cursor.count)
            while (cursor.moveToNext()) {
                tracks += Track(
                    id = cursor.getLong(idCol),
                    title = cursor.getString(titleCol).orFallback("Unknown title"),
                    artist = cursor.getString(artistCol).orFallback("Unknown artist"),
                    album = cursor.getString(albumCol).orFallback("Unknown album"),
                    durationMs = cursor.getLong(durationCol),
                )
            }
            return tracks
        }

        private fun String?.orFallback(fallback: String): String =
            if (isNullOrBlank() || this == UNKNOWN) fallback else this
    }
}
