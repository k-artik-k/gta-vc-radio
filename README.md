# Vice City Radio

An offline native Android radio receiver built with Kotlin, Jetpack Compose, Media3 ExoPlayer, a `MediaSessionService`, and Preferences DataStore. Each bundled MP3 is a complete station broadcast, not a track list.

## Discovered recordings

| Asset | Size |
| --- | ---: |
| `EMOTION.mp3` | 54.12 MiB |
| `ESPANT.mp3` | 56.43 MiB |
| `FEVER.mp3` | 57.88 MiB |
| `FLASH.mp3` | 54.75 MiB |
| `KCHAT.mp3` | 47.57 MiB |
| `VCPR.mp3` | 39.55 MiB |
| `VROCK.mp3` | 70.87 MiB |
| `WAVE.mp3` | 60.53 MiB |
| `WILD.mp3` | 62.67 MiB |

The source files in `stations/` are deliberately untouched. Build assets are copied to `app/src/main/assets/stations/`; the runtime catalog recursively enumerates that directory and uses a stable ID based on each relative filename. Known Vice City labels are sorted in station-dial order, then unknown files are sorted case-insensitively.

## Architecture

- `RadioPlaybackService` owns the only `ExoPlayer`, `MediaSession`, audio focus/noisy-headphone behavior, persistent state, and station switching.
- `StationCatalog` discovers the packaged files. `BroadcastClock` is standalone, deterministic, and unit-tested.
- `StationStateStore` saves selected station, power state, duration, and an anchor for every initialized station in DataStore.
- The Compose activity holds only a `MediaController`; it observes Media3 state and sends custom station/power commands. It never holds an audio player.

## Live broadcast model

When a station is first tuned, the service waits for Media3 duration discovery then picks one random position away from the recording's ends. The position is saved with the current wall-clock time. Every return calculates:

`(anchor position + elapsed wall-clock time) modulo that station duration`

The outgoing station is anchored on every switch/power transition and about every 12 seconds while playing. Consequently all broadcasts continue while another station is tuned, while the radio is off, and across normal process recovery. ExoPlayer loops the active recording only; it never switches stations at an MP3 boundary.

## Build and install

Use a JDK 17-23 and an Android API 36 SDK. This workspace's Android Studio bundled JBR is Java 25, which Gradle 8.14 cannot use for Kotlin DSL parsing; `gradle.properties` therefore selects the installed JDK 23 only for this project (without changing global Java settings):

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

The project targets API 36, has `minSdk 26`, and needs no internet permission or account. Playback uses the platform MediaSession notification/lock-screen surface. Exact control presentation is ultimately manufacturer/System UI dependent; seek commands are not advertised by the session.
