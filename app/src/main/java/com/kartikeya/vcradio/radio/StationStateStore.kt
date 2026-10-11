package com.kartikeya.vcradio.radio

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.radioDataStore: DataStore<Preferences> by preferencesDataStore(name = "vice_city_radio")

/** Compact DataStore persistence. Bad or old values fall back to a usable empty radio state. */
class StationStateStore(private val context: Context) {
    private val stateKey = stringPreferencesKey("station_state_v1")

    suspend fun load(): SavedRadioState = runCatching {
        decode(context.radioDataStore.data.first()[stateKey])
    }.getOrDefault(SavedRadioState())

    suspend fun save(state: SavedRadioState) {
        context.radioDataStore.edit { it[stateKey] = encode(state) }
    }

    private fun encode(state: SavedRadioState): String {
        val entries = JSONArray()
        state.stations.values.forEach { anchor ->
            entries.put(JSONObject().apply {
                put("id", anchor.stationId)
                put("duration", anchor.durationMs)
                put("position", anchor.anchorPositionMs)
                put("at", anchor.anchoredAtEpochMs)
                put("initialized", anchor.initialized)
            })
        }
        return JSONObject().apply {
            put("current", state.currentStationId)
            put("powered", state.powered)
            put("stations", entries)
        }.toString()
    }

    private fun decode(raw: String?): SavedRadioState {
        if (raw.isNullOrBlank()) return SavedRadioState()
        val root = JSONObject(raw)
        val states = mutableMapOf<String, StationAnchor>()
        val entries = root.optJSONArray("stations") ?: JSONArray()
        for (i in 0 until entries.length()) {
            val entry = entries.optJSONObject(i) ?: continue
            val id = entry.optString("id").takeIf { it.isNotBlank() } ?: continue
            states[id] = StationAnchor(
                stationId = id,
                durationMs = entry.optLong("duration").coerceAtLeast(0L),
                anchorPositionMs = entry.optLong("position").coerceAtLeast(0L),
                anchoredAtEpochMs = entry.optLong("at").coerceAtLeast(0L),
                initialized = entry.optBoolean("initialized", false),
            )
        }
        return SavedRadioState(
            currentStationId = root.optString("current").takeIf { it.isNotBlank() },
            powered = root.optBoolean("powered", false),
            stations = states,
        )
    }
}
