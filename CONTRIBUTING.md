# Contributing

Media Ecosystem is in experimental pre-alpha development.

## Before changing behavior

1. Read the v1.0.0 Definition of Done.
2. Identify the relevant acceptance-matrix IDs.
3. Record architecture changes in an ADR when appropriate.
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
