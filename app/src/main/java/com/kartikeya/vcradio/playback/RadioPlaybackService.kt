package com.kartikeya.vcradio.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.kartikeya.vcradio.radio.BroadcastClock
import com.kartikeya.vcradio.MainActivity
import com.kartikeya.vcradio.radio.SavedRadioState
import com.kartikeya.vcradio.radio.Station
import com.kartikeya.vcradio.radio.StationAnchor
import com.kartikeya.vcradio.radio.StationCatalog
import com.kartikeya.vcradio.radio.StationStateStore
import com.kartikeya.vcradio.radio.adjacent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The single owner of ExoPlayer, station timelines, and the platform media session. */
@OptIn(UnstableApi::class)
class RadioPlaybackService : MediaSessionService() {
    companion object {
        const val ACTION_PREVIOUS_STATION = "com.kartikeya.vcradio.PREVIOUS_STATION"
        const val ACTION_NEXT_STATION = "com.kartikeya.vcradio.NEXT_STATION"
        const val ACTION_TOGGLE_POWER = "com.kartikeya.vcradio.TOGGLE_POWER"
        const val EXTRA_POWERED = "powered"
        const val EXTRA_STATION_ID = "station_id"
        const val EXTRA_STATION_NAME = "station_name"
        const val EXTRA_ERROR = "error"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val broadcastClock = BroadcastClock()
    private lateinit var player: ExoPlayer
    private lateinit var sessionPlayer: NonSeekableSessionPlayer
    private lateinit var mediaSession: MediaSession
    private lateinit var store: StationStateStore
    private var stations: List<Station> = emptyList()
    private var radioState = SavedRadioState()
    private var pendingStationId: String? = null
    private var lastError: String? = null
    private var loaded = false

    override fun onCreate() {
        super.onCreate()
        store = StationStateStore(applicationContext)
        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            setHandleAudioBecomingNoisy(true)
            repeatMode = Player.REPEAT_MODE_ONE
            addListener(playerListener)
        }
        sessionPlayer = NonSeekableSessionPlayer(player)
        val launchIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        mediaSession = MediaSession.Builder(this, sessionPlayer)
            .setSessionActivity(launchIntent)
            .setCallback(sessionCallback)
            .build()
        configureNotificationButtons()
        serviceScope.launch {
            stations = StationCatalog.fromAssets(assets)
            radioState = store.load().sanitizeFor(stations)
            loaded = true
            if (radioState.currentStationId == null) {
                radioState = radioState.copy(currentStationId = stations.firstOrNull()?.id)
                persist()
            }
            publishSessionState()
            if (radioState.powered) tuneCurrentStation()
        }
        serviceScope.launch {
            while (isActive) {
                delay(12_000)
                if (loaded && player.isPlaying) saveCurrentAnchor()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(ACTION_PREVIOUS_STATION, Bundle.EMPTY))
                .add(SessionCommand(ACTION_NEXT_STATION, Bundle.EMPTY))
                .add(SessionCommand(ACTION_TOGGLE_POWER, Bundle.EMPTY))
                .build()
            // Intentionally expose only playback and metadata. Seek commands are never advertised.
            val playerCommands = Player.Commands.Builder()
                .add(Player.COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
                .add(Player.COMMAND_GET_METADATA)
                .add(Player.COMMAND_GET_TIMELINE)
                .add(Player.COMMAND_GET_AUDIO_ATTRIBUTES)
                .add(Player.COMMAND_GET_VOLUME)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(playerCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            serviceScope.launch {
                when (customCommand.customAction) {
                    ACTION_PREVIOUS_STATION -> selectAdjacent(-1)
                    ACTION_NEXT_STATION -> selectAdjacent(1)
                    ACTION_TOGGLE_POWER -> togglePower()
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) onMediaReady()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!loaded || !playWhenReady || !radioState.powered) {
                if (loaded && !playWhenReady) saveCurrentAnchor()
                publishSessionState()
                return
            }
            // A platform Play after a pause returns to the current live position, not stale audio.
            val current = radioState.currentStationId
            val duration = player.duration
            val anchor = current?.let { radioState.stations[it] }
            if (current != null && current == player.currentMediaItem?.mediaId && anchor != null && duration > 0L) {
                player.seekTo(broadcastClock.positionNow(anchor, duration))
            }
            publishSessionState()
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            lastError = "This station cannot be played. Try another preset."
            pendingStationId = null
            publishSessionState()
        }
    }

    private fun configureNotificationButtons() {
        val previous = SessionCommand(ACTION_PREVIOUS_STATION, Bundle.EMPTY)
        val next = SessionCommand(ACTION_NEXT_STATION, Bundle.EMPTY)
        val power = SessionCommand(ACTION_TOGGLE_POWER, Bundle.EMPTY)
        mediaSession.setMediaButtonPreferences(
            listOf(
                CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                    .setDisplayName("Previous station")
                    .setSessionCommand(previous)
                    .build(),
                CommandButton.Builder(CommandButton.ICON_PLAY)
                    .setDisplayName("Play or pause")
                    .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                    .build(),
                CommandButton.Builder(CommandButton.ICON_NEXT)
                    .setDisplayName("Next station")
                    .setSessionCommand(next)
                    .build(),
                CommandButton.Builder(CommandButton.ICON_STOP)
                    .setDisplayName("Power off")
                    .setSessionCommand(power)
                    .build(),
            ),
        )
    }

    private fun selectAdjacent(delta: Int) {
        if (!loaded || stations.isEmpty()) return
        val target = stations.adjacent(radioState.currentStationId, delta) ?: return
        if (target.id == radioState.currentStationId) return
        saveCurrentAnchor(write = false)
        radioState = radioState.copy(currentStationId = target.id)
        lastError = null
        publishSessionState()
        persist()
        if (radioState.powered) tuneCurrentStation()
    }

    private fun togglePower() {
        if (!loaded || radioState.currentStationId == null) return
        if (radioState.powered) {
            saveCurrentAnchor(write = false)
            radioState = radioState.copy(powered = false)
            player.pause()
            pendingStationId = null
            publishSessionState()
            persist()
        } else {
            radioState = radioState.copy(powered = true)
            lastError = null
            publishSessionState()
            persist()
            tuneCurrentStation()
        }
    }

    private fun tuneCurrentStation() {
        val station = stations.firstOrNull { it.id == radioState.currentStationId } ?: return
        pendingStationId = station.id
        val metadata = metadataFor(station)
        val item = MediaItem.Builder()
            .setMediaId(station.id)
            .setUri("asset:///" + android.net.Uri.encode(station.assetPath, "/"))
            .setMediaMetadata(metadata)
            .build()
        player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = radioState.powered
        publishSessionState()
    }

    private fun onMediaReady() {
        val id = pendingStationId ?: return
        if (id != radioState.currentStationId || id != player.currentMediaItem?.mediaId) return
        val duration = player.duration
        if (duration <= 0L || duration == C.TIME_UNSET) {
            lastError = "This recording has no usable duration."
            publishSessionState()
            return
        }
        val existing = radioState.stations[id]
        val anchor = if (existing?.initialized == true) {
            broadcastClock.anchorAt(id, broadcastClock.positionNow(existing, duration), duration)
        } else {
            broadcastClock.firstAnchor(id, duration)
        }
        radioState = radioState.copy(stations = radioState.stations + (id to anchor))
        player.seekTo(anchor.anchorPositionMs)
        player.playWhenReady = radioState.powered
        pendingStationId = null
        lastError = null
        publishSessionState()
        persist()
    }

    private fun saveCurrentAnchor(write: Boolean = true) {
        val id = radioState.currentStationId ?: return
        if (id != player.currentMediaItem?.mediaId) return
        val duration = player.duration
        if (duration <= 0L || duration == C.TIME_UNSET) return
        radioState = radioState.copy(
            stations = radioState.stations + (id to broadcastClock.anchorAt(id, player.currentPosition, duration)),
        )
        if (write) persist()
    }

    private fun publishSessionState() {
        val station = stations.firstOrNull { it.id == radioState.currentStationId }
        mediaSession.setSessionExtras(Bundle().apply {
            putBoolean(EXTRA_POWERED, radioState.powered)
            putString(EXTRA_STATION_ID, station?.id)
            putString(EXTRA_STATION_NAME, station?.displayName)
            putString(EXTRA_ERROR, lastError)
        })
    }

    private fun metadataFor(station: Station): MediaMetadata = MediaMetadata.Builder()
        .setTitle(station.displayName)
        .setArtist("Vice City Radio")
        .build()

    private fun persist() {
        serviceScope.launch(Dispatchers.IO) { store.save(radioState) }
    }

    override fun onDestroy() {
        if (::player.isInitialized) saveCurrentAnchor()
        if (::mediaSession.isInitialized) mediaSession.release()
        if (::player.isInitialized) player.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // MediaSessionService remains alive for an active radio; an idle radio can be reclaimed normally.
        if (!player.isPlaying) stopSelf()
    }
}

private fun SavedRadioState.sanitizeFor(catalog: List<Station>): SavedRadioState {
    val validIds = catalog.map { it.id }.toSet()
    val migratedAnchors = catalog.mapNotNull { station ->
        val anchor = stations[station.id]
            ?: stations["${station.id}.mp3"]
            ?: stations["${station.id}.m4a"]
            ?: return@mapNotNull null
        station.id to anchor.copy(stationId = station.id)
    }.toMap()
    return copy(
        currentStationId = currentStationId?.let { savedId ->
            when {
                savedId in validIds -> savedId
                savedId.substringBeforeLast('.') in validIds -> savedId.substringBeforeLast('.')
                else -> null
            }
        } ?: catalog.firstOrNull()?.id,
        stations = migratedAnchors.mapValues { (_, anchor) ->
            anchor.copy(
                durationMs = anchor.durationMs.coerceAtLeast(0L),
                anchorPositionMs = anchor.anchorPositionMs.coerceAtLeast(0L),
                anchoredAtEpochMs = anchor.anchoredAtEpochMs.coerceAtLeast(0L),
            )
        },
    )
}

/** The system UI must not expose a disabled progress slider for a radio dial. */
private class NonSeekableSessionPlayer(player: Player) : ForwardingPlayer(player) {
    override fun isCurrentMediaItemSeekable(): Boolean = false
    override fun getDuration(): Long = C.TIME_UNSET
}
