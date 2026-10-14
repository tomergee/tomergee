package com.tomergee.mp3player.ui

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tomergee.mp3player.FakeTrackRepository
import com.tomergee.mp3player.data.PlaylistRepository
import com.tomergee.mp3player.data.db.AppDatabase
import com.tomergee.mp3player.track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Integration of the view model with a real (in-memory) Room database and a fake MediaStore. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LibraryViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var playlists: PlaylistRepository
    private val tracks = FakeTrackRepository(listOf(track(1), track(2), track(3)))
    private lateinit var vm: LibraryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        playlists = PlaylistRepository(db.playlistDao())
        vm = LibraryViewModel(tracks, playlists)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun <T> Flow<T>.await(predicate: (T) -> Boolean): T = runBlocking { withTimeout(5_000) { first(predicate) } }

    private fun scanAndWait() {
        vm.onPermissionResult(true)
        vm.state.await { tracks.calls > 0 && !it.isLoading }
    }

    @Test
    fun `does not scan without permission`() {
        vm.onPermissionResult(false)
        vm.refresh()
        assertEquals(0, tracks.calls)
        assertFalse(vm.state.value.hasPermission)
    }

    @Test
    fun `granting permission scans the library`() {
        scanAndWait()
        assertEquals(listOf(1L, 2L, 3L), vm.state.value.tracks.map { it.id })
        assertEquals(1, tracks.calls)
    }

    @Test
    fun `scan prunes playlist entries for deleted files`() {
        val id = runBlocking { playlists.create("Mix").also { listOf(1L, 2L, 3L).forEach { t -> playlists.addTrack(it, t) } } }
        tracks.tracks = listOf(track(1), track(3))
        scanAndWait()
        playlists.observeTrackIds(id).await { it == listOf(1L, 3L) }
    }

    @Test
    fun `empty scan never wipes playlists`() {
        val id = runBlocking { playlists.create("Mix").also { playlists.addTrack(it, 1) } }
        tracks.tracks = emptyList()
        scanAndWait()
        assertEquals(listOf(1L), runBlocking { playlists.observeTrackIds(id).first() })
    }

    @Test
    fun `security exception during scan revokes permission state`() {
        tracks.error = SecurityException("revoked")
        vm.onPermissionResult(true)
        vm.state.await { !it.hasPermission && !it.isLoading }
    }

    @Test
    fun `unexpected scan failure surfaces an error and retry recovers`() {
        tracks.error = IllegalStateException("disk on fire")
        vm.onPermissionResult(true)
        vm.state.await { it.error == "Couldn't read music: disk on fire" }
        tracks.error = null
        vm.refresh()
        vm.state.await { it.error == null && it.tracks.size == 3 }
    }

    @Test
    fun `playlist detail joins ids with library in playlist order`() {
        scanAndWait()
        val id = runBlocking { playlists.create("Mix").also { listOf(3L, 1L).forEach { t -> playlists.addTrack(it, t) } } }
        val detail = vm.observePlaylistDetail(id).await { it != null && it.tracks.size == 2 }!!
        assertEquals("Mix", detail.name)
        assertEquals(listOf(3L, 1L), detail.tracks.map { it.id })
    }

    @Test
    fun `detail of a deleted playlist emits null`() {
        val id = runBlocking { playlists.create("Gone") }
        vm.deletePlaylist(id)
        vm.observePlaylistDetail(id).await { it == null }
    }

    @Test
    fun `blank playlist name reports an error message`() {
        vm.createPlaylist("  ")
        assertEquals("Playlist name must not be blank", vm.messages.await { true })
    }

    @Test
    fun `create with pending track adds it and adding twice reports duplicate`() {
        vm.createPlaylist("Road trip", thenAddTrackId = 2)
        assertEquals("Created \"Road trip\"", vm.messages.await { true })
        val summary = vm.playlists.await { it.singleOrNull()?.trackCount == 1 }.single()
        vm.addToPlaylist(summary, 2)
        assertEquals("Already in Road trip", vm.messages.await { true })
    }

    @Test
    fun `move and remove go through to the database`() {
        val id = runBlocking { playlists.create("Mix").also { listOf(1L, 2L, 3L).forEach { t -> playlists.addTrack(it, t) } } }
        vm.moveInPlaylist(id, trackId = 3, targetTrackId = 1)
        playlists.observeTrackIds(id).await { it == listOf(3L, 1L, 2L) }
        vm.removeFromPlaylist(id, 1)
        playlists.observeTrackIds(id).await { it == listOf(3L, 2L) }
    }
}
