# Vice City Radio

Vice City Radio is a deliberately minimal, offline Android radio receiver. Each station is one long broadcast recording: music, ads, DJs, and transitions all stay together. Tuning away does not pause other broadcasts; every station continues on its own simulated live clock.

## What it does

- Plays all bundled stations offline through one Media3 ExoPlayer owned by a `MediaSessionService`.
- Persists a random first-tune position, duration, and wall-clock anchor for every station with DataStore.
- Calculates returns as `(anchor position + real elapsed time) modulo station duration`.
- Provides previous station, power, next station, lock-screen controls, audio focus, headphone-unplug handling, background playback, and no seek controls.
- Uses the supplied station logos in the UI and a warm-amber vinyl app icon.

## Stations

| Source recording | Display name | Packaged format |
| --- | --- | --- |
| `WILD.mp3` | Wildstyle Pirate Radio | AAC/M4A |
| `FLASH.mp3` | Flash FM | AAC/M4A |
| `KCHAT.mp3` | K-Chat | original MP3 |
| `FEVER.mp3` | Fever 105 | AAC/M4A |
| `VROCK.mp3` | V-Rock | AAC/M4A |
| `VCPR.mp3` | VCPR | original MP3 |
| `ESPANT.mp3` | Radio Espantoso | AAC/M4A |
| `EMOTION.mp3` | Emotion 98.3 | AAC/M4A |
| `WAVE.mp3` | Wave 103 | AAC/M4A |

The catalog is built at runtime from `app/src/main/assets/stations`, recognizes both `.mp3` and `.m4a`, and gives known Vice City stations a stable dial order. Other audio files are appended alphabetically.

## Audio and repository policy

The recordings are copyright-protected and large, so `stations/` and `app/src/main/assets/stations/` are intentionally ignored by Git. They remain on your local machine and are included in a locally built APK, but are not pushed to GitHub.

Put source MP3s in `stations/`, then package them:

```powershell
.\tools\prepare-stations.ps1
```

The packaging script never changes the originals. It retains the two 64 kbps source MP3s and transcodes the seven 128 kbps ones to 80 kbps AAC/M4A, reducing the packaged audio by about 29% while preserving every broadcast and feature. For a byte-for-byte MP3 package instead:

```powershell
.\tools\prepare-stations.ps1 -KeepOriginalMp3
```

## Build and install

An Android API 36 SDK is required. The Android Studio JBR on this machine is Java 25, while Gradle 8.14's Kotlin DSL needs Java 23 or lower; the project-local Gradle configuration selects the installed JDK 23 without changing global Java settings.

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## GitHub cleanup

The first commit already contains the audio twice (`stations/` and the app assets), so `.gitignore` alone cannot remove those copies from GitHub history. This checkout has already had both directories removed from Git's index with `git rm --cached`; the local files are untouched. Commit that staged removal with:

```powershell
git add .gitignore tools/prepare-stations.ps1 README.md
git commit -m "Keep local radio recordings out of the repository"
git push
```

If another checkout still tracks them, run `git rm -r --cached stations app/src/main/assets/stations` before that commit.

If you also need to reclaim the existing GitHub history, use `git filter-repo` (or GitHub's documented history rewrite process) and force-push. That rewrites commit IDs, so coordinate with anyone who cloned the repo first:

```powershell
git filter-repo --path stations --path app/src/main/assets/stations --invert-paths
git push --force --all
git push --force --tags
```

## Platform note

The app removes seek commands and reports the session as non-seekable. Android manufacturers can still vary the exact media-panel layout, but the app does not offer seek operations.
