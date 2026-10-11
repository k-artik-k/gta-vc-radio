package com.kartikeya.vcradio.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StationStateLogicTest {
    private val stations = listOf(
        Station("wild", "stations/WILD.mp3", "Wildstyle Pirate Radio", 1),
        Station("flash", "stations/FLASH.mp3", "Flash FM", 2),
        Station("wave", "stations/WAVE.mp3", "Wave 103", 3),
    )

    @Test fun `previous and next navigation wrap`() {
        assertEquals("wave", stations.adjacent("wild", -1)?.id)
        assertEquals("wild", stations.adjacent("wave", 1)?.id)
        assertEquals("flash", stations.adjacent("unknown", 1)?.id)
        assertNull(emptyList<Station>().adjacent("any", 1))
    }

    @Test fun `saved station state survives process-style reconstruction`() {
        val saved = SavedRadioState(
            currentStationId = "flash",
            powered = true,
            stations = mapOf("flash" to StationAnchor("flash", 3_600_000, 120_000, 500, true)),
        )
        val restored = saved.copy(stations = saved.stations.toMap())

        assertEquals("flash", restored.currentStationId)
        assertEquals(120_000, restored.stations.getValue("flash").anchorPositionMs)
        assertEquals(true, restored.powered)
    }

    @Test fun `final rapid switch target remains the target`() {
        val finalTarget = sequenceOf(1, 1, -1, 1).fold("wild") { selected, delta ->
            stations.adjacent(selected, delta)?.id ?: selected
        }
        assertEquals("wave", finalTarget)
    }
}
