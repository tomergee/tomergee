package com.tomergee.mp3player.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test fun `formats minutes and seconds`() = assertEquals("3:05", formatDuration(185_000))
    @Test fun `formats zero`() = assertEquals("0:00", formatDuration(0))
    @Test fun `clamps negative values`() = assertEquals("0:00", formatDuration(-5_000))
    @Test fun `truncates partial seconds`() = assertEquals("0:59", formatDuration(59_999))
    @Test fun `formats hours`() = assertEquals("1:02:03", formatDuration(3_723_000))
}
