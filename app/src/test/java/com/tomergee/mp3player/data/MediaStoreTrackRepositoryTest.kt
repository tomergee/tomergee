package com.tomergee.mp3player.data

import android.database.MatrixCursor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaStoreTrackRepositoryTest {

    private fun cursor(vararg rows: Array<Any?>) =
        MatrixCursor(MediaStoreTrackRepository.PROJECTION).apply { rows.forEach(::addRow) }

    @Test
    fun `maps all columns in order`() {
        val tracks = MediaStoreTrackRepository.mapTracks(
            cursor(arrayOf(7L, "Song", "Band", "LP", 123_000L), arrayOf(9L, "Other", "Solo", "EP", 1L)),
        )
        assertEquals(listOf(Track(7, "Song", "Band", "LP", 123_000), Track(9, "Other", "Solo", "EP", 1)), tracks)
    }

    @Test
    fun `replaces unknown and missing metadata with readable fallbacks`() {
        val track = MediaStoreTrackRepository.mapTracks(cursor(arrayOf(1L, null, "<unknown>", " ", 0L))).single()
        assertEquals("Unknown title", track.title)
        assertEquals("Unknown artist", track.artist)
        assertEquals("Unknown album", track.album)
    }

    @Test
    fun `empty cursor yields empty list`() {
        assertTrue(MediaStoreTrackRepository.mapTracks(cursor()).isEmpty())
    }
}
