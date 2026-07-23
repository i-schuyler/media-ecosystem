# Contributing

Media Ecosystem is open source under the [Apache License 2.0](LICENSE) and
remains in experimental pre-alpha development.

## Before changing behavior

1. Read the [v1.0.0 Definition of Done](docs/product/Media_Ecosystem_v1.0.0_DoD.md),
   [roadmap](docs/product/ROADMAP_v1.md),
   [acceptance matrix](docs/product/V1_ACCEPTANCE_MATRIX.md), and
   [foundation decisions](docs/architecture/FOUNDATION_DECISIONS.md).
2. Identify the relevant DoD sections and acceptance IDs.
3. Follow the [agent instructions](AGENTS.md) and record architecture changes
   in an ADR when appropriate.
4. Use synthetic data only.

## Pull requests

Pull requests should:

- Remain reviewable as a coherent implementation slice.
- Explain product and architecture effects.
- Include tests for new behavior.
- Avoid unrelated cleanup.
- Identify privacy, migration, deletion, and compatibility risks.
- Update documentation when behavior changes.

Changes involving identity, synchronization, file mutation, trash, undo,
migration, or duplicate cleanup require explicit invariant and failure tests.

## Privacy

Do not commit real audio, listening history, credentials, personal paths,
runtime databases, or private synchronization state.

## Contributions and license

Unless explicitly stated otherwise, contributions intentionally submitted for
inclusion in Media Ecosystem are provided under the same
[Apache License 2.0](LICENSE) that applies to the project.
