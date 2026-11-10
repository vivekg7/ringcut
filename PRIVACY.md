# Privacy

Ringcut works entirely on your phone.

- **No network.** The app does not declare the `INTERNET` permission, so Android
  will not let it connect to anything. There are no ads, analytics, crash reporters
  or accounts.
- **Files you open** are read through the system file picker, the Photo Picker or the
  Android share sheet. Ringcut only ever gets access to the single file you hand it,
  and only reads it to draw the waveform, play the preview and cut the tone.
- **Tones you save** are written to your phone's shared storage (the Ringtones,
  Notifications, Alarms or Music folder). They stay there, under your control, even
  if you uninstall the app.
- **Cache.** Ringcut keeps a small cache of waveform data (peak levels, not audio)
  in its private cache folder so reopening a file is fast. Android may clear it at any
  time, and so can you from the app's storage settings. Temporary export files are
  deleted as soon as the tone is saved.
- **System settings.** If you allow "Modify system settings", Ringcut uses it only to
  set the tone you choose as the default ringtone, notification or alarm sound.
- **Backups.** The app opts out of Android cloud backup and device transfer; it has
  nothing of yours worth backing up beyond the tones, which live in shared storage.

Questions: vivek@ivy.homes
