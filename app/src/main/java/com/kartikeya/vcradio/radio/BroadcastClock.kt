package com.kartikeya.vcradio.radio

/** Injectable dependencies keep radio timing deterministic in unit tests. */
fun interface WallClock { fun nowEpochMs(): Long }
fun interface PositionRandom { fun nextLong(boundExclusive: Long): Long }

object SystemWallClock : WallClock { override fun nowEpochMs() = System.currentTimeMillis() }

/**
 * Calculates the position of a recording as though its broadcast never stopped.
 * It deliberately performs no scheduling: ExoPlayer is responsible for continuous playback.
 */
class BroadcastClock(
    private val clock: WallClock = SystemWallClock,
    private val random: PositionRandom = PositionRandom { bound -> kotlin.random.Random.nextLong(bound) },
) {
    fun ensureInitial(existing: StationAnchor?, stationId: String, durationMs: Long): StationAnchor =
        existing?.takeIf { it.initialized && it.durationMs > 0L } ?: firstAnchor(stationId, durationMs)

    fun firstAnchor(stationId: String, durationMs: Long): StationAnchor {
        if (durationMs <= 0L) return StationAnchor(stationId = stationId)
        // Long recordings start away from both the introduction and the final fade-out.
        val margin = minOf(30_000L, durationMs / 10L)
        val lower = margin.coerceAtMost((durationMs - 1).coerceAtLeast(0L))
        val upperExclusive = (durationMs - margin).coerceAtLeast(lower + 1L)
        val range = (upperExclusive - lower).coerceAtLeast(1L)
        val sampled = random.nextLong(range).coerceIn(0L, range - 1L)
        return StationAnchor(
            stationId = stationId,
            durationMs = durationMs,
            anchorPositionMs = lower + sampled,
            anchoredAtEpochMs = clock.nowEpochMs(),
            initialized = true,
        )
    }

    fun positionNow(anchor: StationAnchor, actualDurationMs: Long = anchor.durationMs): Long =
        positionAt(anchor, actualDurationMs, clock.nowEpochMs())

    fun positionAt(anchor: StationAnchor, actualDurationMs: Long, nowEpochMs: Long): Long {
        if (!anchor.initialized || actualDurationMs <= 0L) return 0L
        val base = floorMod(anchor.anchorPositionMs, actualDurationMs)
        // A backwards device-clock change should not produce a negative radio position.
        val elapsed = (nowEpochMs - anchor.anchoredAtEpochMs).coerceAtLeast(0L)
        return (base + (elapsed % actualDurationMs)) % actualDurationMs
    }

    fun anchorAt(stationId: String, positionMs: Long, durationMs: Long): StationAnchor = StationAnchor(
        stationId = stationId,
        durationMs = durationMs.coerceAtLeast(0L),
        anchorPositionMs = if (durationMs > 0) floorMod(positionMs, durationMs) else 0L,
        anchoredAtEpochMs = clock.nowEpochMs(),
        initialized = durationMs > 0L,
    )

    private fun floorMod(value: Long, modulus: Long): Long = ((value % modulus) + modulus) % modulus
}
