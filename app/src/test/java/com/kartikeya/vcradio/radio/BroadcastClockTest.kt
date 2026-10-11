package com.kartikeya.vcradio.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BroadcastClockTest {
    private class FakeClock(var now: Long) : WallClock { override fun nowEpochMs() = now }

    @Test fun `first tune starts within the recording away from both edges`() {
        val clock = FakeClock(1_000)
        val radio = BroadcastClock(clock, PositionRandom { 7 })
        val anchor = radio.firstAnchor("flash", 600_000)

        assertTrue(anchor.initialized)
        assertTrue(anchor.anchorPositionMs >= 30_000)
        assertTrue(anchor.anchorPositionMs < 570_000)
        assertEquals(1_000, anchor.anchoredAtEpochMs)
    }

    @Test fun `an initialized station never draws a new random starting position`() {
        var draws = 0
        val radio = BroadcastClock(FakeClock(0), PositionRandom { draws++; 3 })
        val original = StationAnchor("flash", 100_000, 45_000, 0, true)

        assertEquals(original, radio.ensureInitial(original, "flash", 100_000))
        assertEquals(0, draws)
    }

    @Test fun `position advances by elapsed wall clock and wraps at recording end`() {
        val clock = FakeClock(10_000)
        val radio = BroadcastClock(clock, PositionRandom { 0 })
        val anchor = StationAnchor("wave", 100_000, 95_000, 10_000, true)

        clock.now += 5 * 60_000
        assertEquals(95_000, radio.positionNow(anchor)) // five full 60-second cycles
        assertEquals(15_000, radio.positionAt(anchor, 100_000, 30_000))
    }

    @Test fun `each station uses its own duration`() {
        val radio = BroadcastClock(FakeClock(0), PositionRandom { 0 })
        val shortStation = StationAnchor("short", 60_000, 50_000, 0, true)
        val longStation = StationAnchor("long", 120_000, 50_000, 0, true)

        assertEquals(50_000, radio.positionAt(shortStation, shortStation.durationMs, 60_000))
        assertEquals(110_000, radio.positionAt(longStation, longStation.durationMs, 60_000))
        assertEquals(0, radio.positionAt(shortStation, 0, 60_000))
    }

    @Test fun `invalid values remain safe`() {
        val radio = BroadcastClock(FakeClock(0), PositionRandom { 0 })
        val invalid = StationAnchor("missing", -1, -5, -10, false)

        assertEquals(0, radio.positionNow(invalid))
        assertFalse(radio.firstAnchor("bad", 0).initialized)
    }
}
