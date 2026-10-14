package com.tomergee.mp3player.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tomergee.mp3player.FakeAudioPlayer
import com.tomergee.mp3player.player.RepeatMode
import com.tomergee.mp3player.track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerViewModelTest {
    private val player = FakeAudioPlayer()
    private val vm = PlayerViewModel(player)

    @Test
    fun `playTracks forwards queue snapshot and start index`() {
        val tracks = listOf(track(1), track(2), track(3))
        vm.playTracks(tracks, 1)
        assertEquals(tracks, player.queue)
        assertEquals(2L, vm.state.value.currentTrack?.id)
    }

    @Test
    fun `togglePlayPause flips playback`() {
        vm.playTracks(listOf(track(1)), 0)
        vm.togglePlayPause()
        assertFalse(vm.state.value.isPlaying)
        vm.togglePlayPause()
        assertTrue(vm.state.value.isPlaying)
    }

    @Test
    fun `cycleRepeatMode walks through all modes`() {
        val seen = List(3) { vm.cycleRepeatMode(); vm.state.value.repeatMode }
        assertEquals(listOf(RepeatMode.ALL, RepeatMode.ONE, RepeatMode.OFF), seen)
    }

    @Test
    fun `toggleShuffle flips shuffle`() {
        vm.toggleShuffle()
        assertTrue(vm.state.value.shuffle)
        vm.toggleShuffle()
        assertFalse(vm.state.value.shuffle)
    }

    @Test
    fun `transport controls delegate`() {
        vm.next(); vm.previous(); vm.seekTo(42)
        assertEquals(1, player.nextCalls)
        assertEquals(1, player.previousCalls)
        assertEquals(42L, player.lastSeek)
    }

    @Test
    fun `backgrounding pauses`() {
        vm.playTracks(listOf(track(1)), 0)
        vm.onAppBackgrounded()
        assertFalse(vm.state.value.isPlaying)
    }

    @Test
    fun `clearing the view model releases the player`() {
        val fake = FakeAudioPlayer()
        val store = ViewModelStore()
        ViewModelProvider(store, viewModelFactory { initializer { PlayerViewModel(fake) } })[PlayerViewModel::class.java]
        store.clear()
        assertTrue(fake.released)
    }
}
