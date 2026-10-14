# MP3 Player (Android)

A simple foreground MP3 player for Android 8.0+ (API 26). Kotlin, Jetpack Compose, Media3 ExoPlayer, Room.

## Features
- **Songs**: every MP3 on the device (read via `MediaStore`, `audio/mpeg` + `IS_MUSIC`), sorted by title. Tap to play the whole list from that song.
- **Playlists**: create, rename, delete; add songs from the Songs tab; remove and drag-to-reorder inside a playlist. A song appears at most once per playlist.
- **Controls**: play/pause, previous (restarts the track if past ~3s), next, seek bar with elapsed/total time, shuffle, repeat (off → all → one).
- Pauses when the app leaves the foreground, when headphones are unplugged, and on audio-focus loss (e.g. a phone call).

## Design notes
| Area | Decision |
|---|---|
| Track identity | Playlists store the MediaStore `_ID`. Each scan prunes entries for files that no longer exist; an **empty** scan never prunes (protects against an unmounted SD card wiping playlists). Missing tracks are also hidden at display time. |
| Ordering | `playlist_tracks(playlist_id, track_id, position)` with PK `(playlist_id, track_id)`. Positions are kept dense; every mutation (add/remove/move/prune) is a single Room transaction. |
| Reorder | Drag updates a local list for smooth feedback; on drop one id-based move (`track → slot of target track`) is persisted, so a stale UI list can't move the wrong row. |
| Playback queue | A snapshot of the list you played from. Editing a playlist doesn't disturb what's currently playing. |
| Threading | MediaStore scan on `Dispatchers.IO`; Room suspend APIs; prune deletes in chunks of 500 to stay under SQLite's 999-variable limit. Progress polls every 500 ms only while playing. |
| Lifecycle | `PlayerViewModel` owns ExoPlayer so playback survives rotation; released in `onCleared`. Paused in `Activity.onStop`. |

```
data/      Track, TrackRepository (MediaStore), PlaylistRepository, db/ (Room entities + DAO)
player/    AudioPlayer interface, ExoAudioPlayer, PlayerState, RepeatMode
ui/        LibraryViewModel, PlayerViewModel, Compose screens, PlayerBar
```

## Build & test
Requires JDK 17+ and the Android SDK (platform 35).

```bash
./gradlew assembleDebug              # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest          # JVM: unit + Robolectric (Room, ViewModels, Compose UI, E2E)
./gradlew connectedDebugAndroidTest  # E2E on a device/emulator
```

`src/sharedTest` holds the end-to-end flow test (launches `MainActivity`, creates/renames/opens/deletes a playlist). It runs both under Robolectric and as an instrumented test.
