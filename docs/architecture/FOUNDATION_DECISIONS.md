# Foundation Decisions

These decisions are part of the Media Ecosystem v1.0.0 product contract.

1. **Custom canonical core.** No third-party media application or database is authoritative.
2. **Public pre-alpha repository.** Code, schemas, documentation, tests, and synthetic fixtures live in one low-attention public repository. Personal runtime data stays outside Git.
3. **Google Drive transports metadata, not audio.** Drive carries journals, snapshots, exports, and backups only.
4. **Local-network audio transfer.** v1 transfers audio directly between registered devices on the local network, with resume and hash verification. Remote relay is deferred.
5. **Immutable identities.** Logical tracks and folders have stable IDs independent of path, tags, and content. Each physical copy has a separate File Instance ID.
6. **No silent guessing.** When identity evidence or destructive intent is insufficient, the system pauses and asks.
7. **Event-derived personal state.** Plays, total played time, ratings, resume positions, playlists, and operations synchronize through idempotent events plus compacted snapshots.
8. **External canonical state.** Routine listening data does not rewrite audio files.
9. **Device-local queues and active timers.** They persist locally but never overwrite another device's state.
10. **One intended copy per device.** Duplicate cleanup retains at most one physical copy on each device intended to store the track. No global-single-copy option exists.
11. **Recoverable destruction.** Deletions go to managed trash. Undo uses compensating events. Stale operations fail safely.
12. **Human-editable configuration.** Supported defaults are editable in app settings and in one documented text config. Secrets are separate.
13. **Missing storage is not deletion.** Disconnected or unmounted storage remains unavailable until verified.
14. **Metadata backup is not audio backup.** The UI distinguishes recoverable metadata from the number and health of physical audio copies.
15. **Apache-2.0 licensing.** Media Ecosystem is open source under the Apache License 2.0. Contributions use the same license unless explicitly stated otherwise; the project remains experimental pre-alpha software.
16. **Evidence before architecture.** Phase 1 capability spikes follow the documented protocol and supported-device matrix. Trying a candidate does not make it canonical; the production stack is selected only after the required proofs and comparison ADR are complete.
