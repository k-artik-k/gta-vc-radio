package com.kartikeya.vcradio.radio

/** A packaged broadcast recording. The asset path, rather than a display label, is its identity. */
data class Station(
    val id: String,
    val assetPath: String,
    val displayName: String,
    val preset: Int,
)

data class StationAnchor(
    val stationId: String,
    val durationMs: Long = 0L,
    val anchorPositionMs: Long = 0L,
    val anchoredAtEpochMs: Long = 0L,
    val initialized: Boolean = false,
)

data class SavedRadioState(
    val currentStationId: String? = null,
    val powered: Boolean = false,
    val stations: Map<String, StationAnchor> = emptyMap(),
)

fun List<Station>.adjacent(currentId: String?, delta: Int): Station? {
    if (isEmpty()) return null
    val current = indexOfFirst { it.id == currentId }.takeIf { it >= 0 } ?: 0
    val index = ((current + delta) % size + size) % size
    return this[index]
}
