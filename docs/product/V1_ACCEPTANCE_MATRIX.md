# Media Ecosystem v1.0.0 Acceptance Matrix

The [Phase 1 capability issue catalog](../implementation/PHASE_1_CAPABILITY_ISSUES.md)
maps proof work to these acceptance IDs. Planning or completing a spike does
not by itself mark an acceptance condition as passed; evidence must cover the
documented validation matrix and release environment.

| ID | Area | Acceptance condition |
|---|---|---|
| ID-01 | Track identity | Rename, move, retag, transcode, and explicit media replacement preserve Track ID. |
| ID-02 | Physical identity | Intended copies on two devices share Track ID and have distinct File Instance IDs. |
| ID-03 | Missing evidence | A file without deterministic identity evidence is never silently merged. |
| FS-01 | Explorer access | Indexed files remain browseable and openable in Android and Windows file explorers. |
| FS-02 | Unmounted root | Removing storage marks items unavailable and creates no delete operations. |
| PB-01 | Formats | MP3 V0, MP3 320, FLAC, AAC, Ogg Vorbis, ALAC, WAV, and AIFF play on both platforms. |
| PB-02 | Background | Android playback continues screen-off and exposes media controls. |
| PB-03 | Queue | Queue, current track, position, shuffle, and repeat survive restart. |
| PB-04 | Clear Queue | Clear Queue works and Undo restores the prior queue. |
| PB-05 | Windows controls | Windows playback exposes working system media controls. |
| RP-01 | Resume lower bound | A saved position below 60 seconds does not prompt under defaults. |
| RP-02 | Resume upper bound | A saved position within the final 120 seconds does not prompt under defaults. |
| RP-03 | Resume sync | A meaningful position becomes available on another device after sync. |
| ST-01 | Sleep exact | Timer without Finish current track stops at expiry and saves position. |
| ST-02 | Sleep finish | Timer with Finish current track completes the track playing at expiry, then stops. |
| ST-03 | Repeat interaction | Repeat cannot continue playback after the timer latches the final track. |
| STAT-01 | Play Count | One qualifying session increments once; partial sessions do not combine. |
| STAT-02 | Total time | Four separate one-minute sessions produce four minutes total played time. |
| STAT-03 | Idempotency | Re-importing events never double-counts plays or time. |
| PL-01 | Playlist identity | Move or rename never removes a track from playlists. |
| PL-02 | Unavailable item | Unavailable tracks stay ordered in playlists and are visibly marked. |
| AV-01 | Visibility | Show, hide, available-only, and unavailable-only controls work for tracks and folders. |
| AV-02 | Folder counts | Folder rows show accurate available and total counts. |
| SY-01 | Offline merge | Supported offline changes on two devices merge without loss or duplication. |
| SY-02 | Clock skew | Incorrect device time cannot corrupt deterministic event ordering. |
| SY-03 | Diagnostics | UI reports last sync, pending events, operations, transfers, and conflicts. |
| TR-01 | No Drive audio | Audio bytes are never uploaded to Google Drive. |
| TR-02 | Resumable transfer | Interrupted local transfer resumes and verifies by hash. |
| OP-01 | Stale target | A changed file prevents an old destructive operation from executing. |
| OP-02 | Trash | Delete moves the file to managed trash and records restoration data. |
| OP-03 | Undo | Undo emits a safe compensating operation after synchronization. |
| OP-04 | Last copy | User is warned before deleting the last verified physical copy. |
| DU-01 | Per-device scope | One intended copy on each of two devices is not classified as redundant. |
| DU-02 | Same-device duplicate | Two exact copies on one device are detected with both locations shown. |
| DU-03 | Cleanup | Per-device cleanup preserves logical metadata and moves extras to trash. |
| DU-04 | No global option | No action offers to keep only one physical copy across the ecosystem. |
| CFG-01 | Shared config | File editing and app settings modify the same effective setting. |
| CFG-02 | Validation | Invalid config never replaces the last valid config. |
| CFG-03 | Precedence | Device override, synced default, and built-in default are visible and correct. |
| REST-01 | Clean restore | A clean install restores metadata and relinks surviving audio. |
| REST-02 | Migration | Schema migration preserves identity, playlists, and statistics. |
| PRIV-01 | Repository privacy | Repository and CI contain synthetic data only. |
