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
VPS evidence, completed Android internal/removable-storage observations, and
remaining Windows and app-level SAF follow-up for issues #6 through #10. Phase
1 is not complete, and no production technology stack has been selected.

## License

Media Ecosystem is licensed under the [Apache License 2.0](LICENSE).
