# Media Ecosystem

- **Status:** Experimental pre-alpha open-source planning
- **Target:** v1.0.0
- **Platforms:** Android and Windows 11
- **License:** [Apache-2.0](LICENSE)

Media Ecosystem is a custom, offline-first, cross-device music player and library system. It keeps ordinary audio files browseable in normal file explorers while synchronizing playlists, ratings, listening statistics, resume positions, device availability, and deterministic file operations.

Media Ecosystem is open source, but it remains experimental pre-alpha software
and is not yet supported for irreplaceable libraries.

## Canonical project documents

- [v1.0.0 Definition of Done](docs/product/Media_Ecosystem_v1.0.0_DoD.md)
- [v1 roadmap](docs/product/ROADMAP_v1.md)
- [v1 acceptance matrix](docs/product/V1_ACCEPTANCE_MATRIX.md)
- [Foundation decisions](docs/architecture/FOUNDATION_DECISIONS.md)
- [Repository bootstrap plan](docs/implementation/REPO_BOOTSTRAP_PLAN.md)
- [Privacy and fixture policy](docs/privacy/PRIVACY_AND_FIXTURE_POLICY.md)
- [Supported test-device matrix](docs/implementation/SUPPORTED_TEST_DEVICE_MATRIX.md)
- [Capability-spike protocol](docs/implementation/CAPABILITY_SPIKE_PROTOCOL.md)
- [Phase 1 capability issue catalog](docs/implementation/PHASE_1_CAPABILITY_ISSUES.md)

Repository work also follows the durable [agent instructions](AGENTS.md).

## Current phase

Phase 0 is complete and Phase 1 capability proofs are active. The disposable
[shared-core foundations harness](spikes/shared-core-foundations/README.md) and
[evidence index](docs/spikes/phase-1/shared-core-foundations/README.md) cover the
VPS evidence and completed Android internal/removable-storage and Windows
internal-NTFS observations for issues #6 through #10. Portable paths (#6),
identity sidecars (#7), and the event reference model (#9) have evidenced their
existing exit criteria; durability, remaining Android resources, app-level SAF,
Windows playback, Windows codecs, and architecture comparison remain.

The disposable
[Android platform proof](spikes/android-platform-proof/README.md) and its
[evidence index](docs/spikes/phase-1/android-platform-proof/README.md) record
verified Samsung-tablet evidence for issues #2, #3, and the Android half of #5.
Persisted SAF read/write permission and marker access survived reboot. Android
background/system-control evidence satisfies issue #3 for this disposable
candidate, and all six amended v1 formats—MP3 V0, MP3 320, FLAC, AAC, Ogg
Vorbis, and WAV—pass on Android. Issue #2 remains open for an actual unavailable
transition and safe relink evidence; issue #5 remains open for the exact
six-format Windows matrix. Phase 1 is not complete, and no production
technology stack has been selected.

The disposable
[Windows platform proof](spikes/windows-platform-proof/README.md) and its
[evidence index](docs/spikes/phase-1/windows-platform-proof/README.md) prepare
one pinned, self-contained .NET 8 `win-x64` diagnostic for issue #4 and the
Windows half of issue #5. Its exact-six fixture verification, bounded PB-01
runner, automatic MediaPlayer/SMTC lifecycle workflow, private restart
checkpoint, sanitized seven-member evidence export, deterministic self-tests,
and Windows CI are tooling only. No Surface Book 3 playback, codec, system
control, sleep/wake, restart, or evidence-ZIP pass is claimed yet; issues #4
and #5 remain open.

## License

Media Ecosystem is licensed under the [Apache License 2.0](LICENSE).
