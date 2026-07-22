# Media Ecosystem v1.0.0 — Definition of Done

**Status:** Approved implementation baseline  
**Date frozen:** 2026-07-22  
**Scope:** Single-user, offline-first media ecosystem for Android and Windows 11

## Product outcome

Media Ecosystem is a custom cross-device player and library system that:

- Keeps audio files in ordinary browseable folders under the logical `Music & Meditation` root.
- Plays the library on Android and Windows 11.
- Synchronizes playlists, ratings, Play Count, Total Played Time, resume positions, availability, and deterministic file operations.
- Supports devices that hold only selected subsets of the logical library.
- Works offline and converges after reconnecting.
- Uses its own canonical data model rather than a third-party player database.

## Required formats

MP3 V0, MP3 320, FLAC, AAC, Ogg Vorbis, ALAC, WAV, and AIFF must play on both platforms.

## Identity and paths

- Every logical track has an immutable globally unique Track ID.
- Every physical copy has a File Instance ID tied to Track ID and Device ID.
- Managed folders have immutable Folder IDs.
- IDs are independent of filenames, paths, tags, duration, and hashes.
- Track ID survives Media Ecosystem-managed rename, move, retag, transcode, and explicit media replacement.
- Identity is mirrored in canonical state and, where safe, an embedded field or human-readable sidecar.
- If deterministic identity evidence is lost, the app offers `Assign existing Track ID` or `Register as new`; it never guesses.
- Shared paths are root-relative. Absolute paths remain device-local.
- Missing or unmounted storage means unavailable, not deleted.
- A device may relink an existing root after removable-storage identifiers change.

## Canonical data

The database and synchronized human-readable state are canonical for IDs, playlists, pinning, ratings, Date Added, Play Count, Total Played Time, listening events, resume positions, availability, queues, file operations, trash, undo, sync, conflicts, and schema versions.

Routine listening never rewrites the audio file. Explicit tag editing may write title, artist, album, album artist, track/disc number, year, genre, and artwork.

## Playback

v1 includes play, pause, seek, previous, next, volume, queue, shuffle, repeat track, repeat queue, background/screen-off playback, Android lock-screen controls, Windows media controls, and current-track metadata/artwork.

## Persistent queue

Each device has its own queue. It persists ordered tracks, current track, position, shuffle, and repeat across restart and reboot. It supports add, remove, reorder, Clear Queue, and Undo Clear Queue. Cross-device queue handoff is deferred.

## Resume position

Default resume prompt rules:

- Saved position is at least 60 seconds from the beginning.
- At least 120 seconds remain before the end.

The prompt offers Resume or Start from beginning. Thresholds are configurable in app settings and the text config. Position checkpoints occur periodically and on pause, stop, track change, and clean shutdown. Completing a track clears active resume state but preserves history and statistics.

## Sleep timer

Choices include 5, 10, 15, 30, 45, 60, and 90 minutes plus custom duration.

`Finish current track` is an independent checkbox:

- Disabled: stop at timer expiry.
- Enabled: when the timer expires, the track playing at that moment becomes the final track, finishes naturally, and playback stops.
- Repeat behavior is suspended after expiry.
- If the user selects another track after expiry, that track becomes the final track and the UI says `Sleep after this track`.

The timer uses wall-clock time, continues while paused and screen-off, is device-local, saves resume position, and can be extended, changed, or canceled. It may persist across app restart by storing a deadline, but cancels on full device reboot.

## Listening statistics

### Play Count

Default per-session threshold is the lesser of 50% of duration or four minutes of active playback. Each session adds at most one play. Seeking does not count skipped material. Separate partial sessions do not combine.

### Total Played Time

Accumulates real elapsed active playback time across devices. Partial sessions count. Paused, buffering, and skipped-by-seek time do not. Replayed portions count again. Simultaneous playback counts independently. Events are additive and deduplicated.

### Other metadata

- Date Added is first registration in Media Ecosystem.
- Ratings support unrated and one through five stars.

## Playlists

- Create, rename, delete, pin, unpin, and organize.
- Add/remove tracks without moving files.
- Preserve order and duplicate entries.
- Keep unavailable tracks visible when requested.
- Create static playlists from folders and add selected folders to playlists.
- Canonical playlists use Track IDs; device M3U8 export includes locally available paths.

## Availability

The system separately tracks intended and actual availability per device. UI supports All, Available here, Unavailable here, Show unavailable, and Hide unavailable. Unavailable tracks/folders are dimmed and labeled; folder rows show available/total counts.

## Audio transfer

Audio never goes through Google Drive. v1 coordinates resumable, hash-verified device-to-device transfer over the local network. If no source device is reachable, the transfer remains pending. Manual copying remains supported and reconciles through Track ID. Remote relay is deferred.

## Metadata synchronization

Metadata uses a user-selected Google Drive account, Wi-Fi-only by default with optional mobile data. Devices maintain device-specific append-only journals, offline outboxes, compacted snapshots, schema versions, and visible diagnostics.

Diagnostics show last sync, pending events, pending file operations, pending transfers, conflicts, local/total availability, and integrity status.

## Conflict behavior

The system never silently guesses:

- Play events: preserve all unique events.
- Total Played Time: sum unique increments.
- Playlist additions: preserve both.
- Ratings: latest causally ordered update wins.
- Resume: latest completed meaningful session wins.
- Rename/rename, move/move, delete/edit, and concurrent playlist reorder: pause and request review.
- Device availability: latest explicit device instruction wins.

Ordering uses Event ID, Device ID, device-local sequence, and display timestamp; wall-clock time is not the sole authority.

## File operations

Long press includes Add to playlist, Open location, Remove from playlist, Rename, Move, Delete, and View identity/instances. Destructive operations validate Track ID, File Instance ID, path, and expected hash. Offline operations remain pending. Changed targets stop for review.

## Trash and Undo

- Deletion moves files to app-managed trash.
- Default retention is 30 days and configurable.
- Trash preserves prior path, identity, and expected hash.
- Permanent deletion requires explicit emptying or retention expiry.
- The app warns before deleting the last verified physical copy.
- Devices can be retired.

Undo Last Action is persistent, one level deep, names the action, and emits a compensating event after synchronization. It applies to playlist edits, ratings, queue edits, Clear Queue, move, rename, delete/restore, availability, and duplicate cleanup. Permanent trash emptying is not undoable.

## Duplicate detection

Deterministic duplicate categories are:

1. Multiple files carrying the same Track ID on one device.
2. Multiple byte-identical files on one device.
3. Multiple registered instances where only one is intended on that device.

Identical copies on separate intended devices are valid.

The primary action is `Keep at most one copy on each intended device`. Review shows every device, folder, filename, ID, hash, size, format, modified time, intended status, proposed retained copy, and proposed removals. Cleanup uses trash and Undo. Changed targets invalidate stale plans.

The app never offers `Keep only one physical file in the entire ecosystem` and does not automatically merge merely similar or non-byte-identical recordings.

## Configuration

A documented human-readable config, such as TOML, is editable both manually and through app settings. Both methods update the same canonical settings. Configuration has a schema version, validation, atomic writes, clear errors, last-valid-state preservation, CLI validation, default restoration, and separate secret storage.

Precedence is:

1. Device override
2. Synced user default
3. Built-in default

The settings UI identifies the effective source.

## Human-readable and AI-friendly interfaces

Provide documented JSON/TOML/JSONL schemas, exports, stable CLI commands, synthetic fixtures, and no proprietary binary-only canonical state. Planned commands include scan, reconcile, sync, doctor, export, config validate, duplicates scan, and stats. AI may generate playlists and reports through documented interfaces but cannot silently perform destructive operations.

## Restore and migration

v1 is incomplete until a clean install can restore metadata, relink surviving audio without losing IDs, migrate schemas safely, recover from interrupted writes, and distinguish metadata recovery from audio recovery.

## Repository posture

One low-attention public pre-alpha repository contains code, schemas, docs, tests, and synthetic fixtures. Personal data, credentials, paths, and listening history are never committed. A private live-statistics Git repository is not part of v1.

## Outside v1.0.0

Spotify/SoundCloud integration, automatic Bandcamp sync, podcasts, video, social sharing, media-server streaming, remote audio relay, cloud audio storage, multi-user accounts, queue handoff, audio-equivalence deduplication, similarity-based automatic merging, automatic destructive AI actions, gapless playback, ReplayGain, album-art repair, timestamp bookmarks, and Continue Listening.
