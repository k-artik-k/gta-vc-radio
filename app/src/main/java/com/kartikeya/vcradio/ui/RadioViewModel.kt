package com.kartikeya.vcradio.ui

import android.app.Application
import android.content.ComponentName
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.kartikeya.vcradio.playback.RadioPlaybackService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RadioUiState(
    val stationName: String = "VICE CITY RADIO",
    val stationId: String? = null,
    val isPlaying: Boolean = false,
    val powered: Boolean = false,
    val connected: Boolean = false,
    val error: String? = null,
)

/** UI owns a controller only. It never creates or manipulates ExoPlayer directly. */
class RadioViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(RadioUiState())
    val uiState: StateFlow<RadioUiState> = _uiState.asStateFlow()
    private var controller: MediaController? = null
    private val controllerFuture: ListenableFuture<MediaController>

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
        override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) = refresh()
        override fun onIsPlayingChanged(isPlaying: Boolean) = refresh()
    }

    private val sessionListener = object : MediaController.Listener {
        override fun onExtrasChanged(controller: MediaController, extras: Bundle) = refresh()
    }

    init {
        val token = SessionToken(
            application,
            ComponentName(application, RadioPlaybackService::class.java),
        )
        controllerFuture = MediaController.Builder(application, token)
            .setListener(sessionListener)
            .buildAsync()
        controllerFuture.addListener(
            {
                runCatching { controllerFuture.get() }.onSuccess { connectedController ->
                    controller = connectedController
                    connectedController.addListener(playerListener)
                    refresh()
                }.onFailure {
                    _uiState.value = RadioUiState(
                        stationName = "SERVICE UNAVAILABLE",
                        error = "Radio service could not connect.",
                    )
                }
            },
            ContextCompat.getMainExecutor(application),
        )
    }

    fun previousStation() = send(RadioPlaybackService.ACTION_PREVIOUS_STATION)
    fun nextStation() = send(RadioPlaybackService.ACTION_NEXT_STATION)
    fun togglePower() = send(RadioPlaybackService.ACTION_TOGGLE_POWER)

    private fun send(action: String) {
        controller?.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), Bundle.EMPTY)
    }

    private fun refresh() {
        val active = controller ?: return
        val metadata = active.mediaMetadata
        val extras = active.sessionExtras
        val powered = extras.getBoolean(RadioPlaybackService.EXTRA_POWERED, false)
        _uiState.value = RadioUiState(
            stationName = extras.getString(RadioPlaybackService.EXTRA_STATION_NAME)
                ?: metadata.title?.toString() ?: "VICE CITY RADIO",
            stationId = extras.getString(RadioPlaybackService.EXTRA_STATION_ID),
            isPlaying = active.isPlaying,
            powered = powered,
            connected = true,
            error = extras.getString(RadioPlaybackService.EXTRA_ERROR),
        )
    }

    override fun onCleared() {
        controller?.removeListener(playerListener)
        MediaController.releaseFuture(controllerFuture)
    }
}
