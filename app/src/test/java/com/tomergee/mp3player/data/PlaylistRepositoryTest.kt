package com.tomergee.mp3player.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tomergee.mp3player.data.db.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaylistRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: PlaylistRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = PlaylistRepository(db.playlistDao(), clock = { 1L })
    }

    @After
    fun tearDown() = db.close()

    private suspend fun ids(playlistId: Long) = repo.observeTrackIds(playlistId).first()

    private suspend fun playlistWith(vararg trackIds: Long): Long {
        val id = repo.create("P")
        trackIds.forEach { repo.addTrack(id, it) }
        return id
    }

    @Test
    fun `create trims name and lists playlists sorted case-insensitively with counts`() = runTest {
        val b = repo.create("  beta ")
        repo.create("Alpha")
        repo.addTrack(b, 1)
        repo.addTrack(b, 2)
        val summaries = repo.observePlaylists().first()
        assertEquals(listOf("Alpha", "beta"), summaries.map { it.name })
        assertEquals(listOf(0, 2), summaries.map { it.trackCount })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank name is rejected`() = runTest { repo.create("   ") }

    @Test(expected = IllegalArgumentException::class)
    fun `overlong name is rejected`() = runTest { repo.create("x".repeat(PlaylistRepository.MAX_NAME_LENGTH + 1)) }

    @Test
    fun `rename updates name`() = runTest {
        val id = repo.create("Old")
        repo.rename(id, " New ")
        assertEquals("New", repo.observePlaylist(id).first()?.name)
    }

    @Test
    fun `add appends in order and ignores duplicates`() = runTest {
        val id = repo.create("P")
        assertTrue(repo.addTrack(id, 10))
        assertTrue(repo.addTrack(id, 20))
        assertFalse(repo.addTrack(id, 10))
        assertEquals(listOf(10L, 20L), ids(id))
    }

    @Test
    fun `same track can live in different playlists`() = runTest {
        val a = playlistWith(5)
        val b = playlistWith(5)
        assertEquals(listOf(5L), ids(a))
        assertEquals(listOf(5L), ids(b))
    }

    @Test
    fun `remove compacts positions so later appends stay at the end`() = runTest {
        val id = playlistWith(1, 2, 3)
        repo.removeTrack(id, 1)
        repo.addTrack(id, 4)
        assertEquals(listOf(2L, 3L, 4L), ids(id))
        assertEquals(listOf(0, 1, 2), db.query("SELECT position FROM playlist_tracks ORDER BY position", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getInt(0)) }
        })
    }

    @Test
    fun `move down places track in target slot`() = runTest {
        val id = playlistWith(1, 2, 3, 4)
        repo.moveTrack(id, trackId = 1, targetTrackId = 3)
        assertEquals(listOf(2L, 3L, 1L, 4L), ids(id))
    }

    @Test
    fun `move up places track in target slot`() = runTest {
        val id = playlistWith(1, 2, 3, 4)
        repo.moveTrack(id, trackId = 4, targetTrackId = 2)
        assertEquals(listOf(1L, 4L, 2L, 3L), ids(id))
    }

    @Test
    fun `move with unknown ids is a no-op`() = runTest {
        val id = playlistWith(1, 2)
        repo.moveTrack(id, trackId = 99, targetTrackId = 1)
        repo.moveTrack(id, trackId = 1, targetTrackId = 99)
        assertEquals(listOf(1L, 2L), ids(id))
    }

    @Test
    fun `delete cascades to entries`() = runTest {
        val id = playlistWith(1, 2)
        repo.delete(id)
        assertNull(repo.observePlaylist(id).first())
        assertTrue(ids(id).isEmpty())
        assertTrue(db.playlistDao().getAllReferencedTrackIds().isEmpty())
    }

    @Test
    fun `prune removes missing tracks from every playlist and keeps order`() = runTest {
        val a = playlistWith(1, 2, 3)
        val b = playlistWith(2, 4)
        val untouched = playlistWith(1, 3)
        repo.pruneMissing(existingTrackIds = setOf(1, 3, 4))
        assertEquals(listOf(1L, 3L), ids(a))
        assertEquals(listOf(4L), ids(b))
        assertEquals(listOf(1L, 3L), ids(untouched))
        repo.addTrack(a, 9)
        assertEquals(listOf(1L, 3L, 9L), ids(a))
    }

    @Test
    fun `prune handles more ids than the SQLite variable limit`() = runTest {
        val id = repo.create("Big")
        val dao = db.playlistDao()
        dao.rewriteOrder(id, (1L..2_500L).toList())
        repo.pruneMissing(existingTrackIds = (1L..2_500L).filter { it % 1000 == 0L }.toSet())
        assertEquals(listOf(1000L, 2000L), ids(id))
    }
}
