package com.tomergee.mp3player.player

import org.junit.Assert.assertEquals
import org.junit.Test

class RepeatModeTest {
    @Test
    fun `cycles off to all to one and back`() {
        assertEquals(RepeatMode.ALL, RepeatMode.OFF.next())
        assertEquals(RepeatMode.ONE, RepeatMode.ALL.next())
        assertEquals(RepeatMode.OFF, RepeatMode.ONE.next())
    }
}
