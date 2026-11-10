# Ringcut

Ad-free ringtone maker and audio extractor for Android. Trim a song, or pull the
sound out of a video, and save it as a ringtone, notification sound, alarm or music
file.

Most ringtone cutters on the Play Store are wrapped in ads and trackers. Ringcut is
free and open source (MIT), with **no ads, no tracking, no analytics, no account, and
no internet permission**.

## Features

- **Open audio** with the system file picker, or **a video** with the Photo Picker.
  You can also share an audio or video file into Ringcut from another app.
- **Waveform editor**: drag the start and end handles (or tap to move the nearer
  one), fine-tune each by ±0.1 s, zoom in for precise cuts and scroll when zoomed.
- **Preview** plays exactly the selection, with a moving playhead.
- **Fade in / fade out**, 0.5–5 s.
- **Save** as Ringtone, Notification, Alarm or Music. The file goes into the matching
  shared folder with the matching MediaStore flag, so it appears in the system sound
  pickers and survives uninstalling the app.
- **Set as default** ringtone, notification or alarm sound.
- **Made with Ringcut**: a list of your tones with play, rename and delete.

## Output format: AAC in .m4a, not MP3

Android ships AAC encoders but no MP3 encoder, so Ringcut saves **AAC audio in an
.m4a file**, which every Android phone plays as a ringtone. The app says "extract
audio", never "convert to MP3", because it doesn't produce MP3.

When the source audio is already AAC (most `.m4a` files and most phone videos) and no
fade is applied, the original audio stream is **copied without re-encoding**: faster,
and no quality loss. Otherwise it is decoded and re-encoded as AAC. The save dialog
tells you which happened.

## Permissions

| Permission                                  | Why                                                                                                                                                                                                                                                           |
| ------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `WRITE_SETTINGS` ("Modify system settings") | Only for _Set as default_. Android requires it to change the default ringtone, notification or alarm sound. It is a special access the user grants on a system screen; the app explains this before sending you there and asks only when you use the feature. |

That is all. In particular:

- **No `INTERNET`.** The app cannot make network connections.
- **No storage or media permission.** Audio is opened through the system document
  picker rather than `READ_MEDIA_AUDIO`, and videos through the Photo Picker, so the
  app only ever sees the one file you choose, never your whole library. Saving and
  managing its own tones in MediaStore needs no permission under scoped storage.
- ExoPlayer's `ACCESS_NETWORK_STATE` and `WAKE_LOCK` are stripped from the merged
  manifest; they are for streaming and background playback, which Ringcut doesn't do.

## How it works

- **Waveform**: `MediaExtractor` + `MediaCodec` decode the first audio track off the
  main thread and reduce it on the fly to one peak per bucket (`PeakAccumulator`), so
  PCM is never held in memory. Buckets are 10 ms, widened for long files so a track
  never needs more than 20 000 of them. The result is cached in memory and on disk
  (in the cache directory, keyed by URI and file size), so reopening a file is
  instant.
- **Preview**: Media3 ExoPlayer with a `ClippingConfiguration`, so the player itself
  stops at the end handle. Fades are imitated with the player volume.
- **Export**: Media3 Transformer with the same clipping, video removed, output pinned
  to AAC. Fades are a small custom `AudioProcessor` (Media3 has no built-in one). The
  file is written to the cache, then copied into MediaStore with `IS_PENDING` so other
  apps never see a half-written tone.
- **Tone list**: a MediaStore query for entries owned by this app. Tones made before a
  reinstall are no longer "owned" and drop off the list, but stay on the phone.
- Pure logic (peak downsampling, time/handle/zoom maths, fade envelope, file naming)
  lives in `app/src/main/java/com/crylo/ringcut/core/` with JVM unit tests.

## Building

Requires JDK 17+. This machine has no system JDK; Android Studio's bundled JBR works:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug testDebugUnitTest lintDebug
./gradlew assembleRelease   # unsigned unless keystore.properties exists
```

Release signing reads an optional, git-ignored `keystore.properties` in the project
root (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). Without it the release
APK is built unsigned.

Minimum Android 12 (API 31), so Material You dynamic colour is always available.

## License

[MIT](LICENSE). Privacy statement: [PRIVACY.md](PRIVACY.md).
