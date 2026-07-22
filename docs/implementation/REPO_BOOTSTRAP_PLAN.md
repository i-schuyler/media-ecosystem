# Repository Bootstrap Plan

## Repository recommendation

- **Name:** `media-ecosystem`
- **Visibility:** Public
- **Posture:** Low-attention experimental pre-alpha
- **Runtime data:** Local databases plus private Google Drive metadata area
- **Second private metadata repository:** Not part of v1.0.0

## Target structure

The repository will grow toward this structure incrementally. Paths that are
not needed for the current foundation phase are intentionally absent.

```text
media-ecosystem/
├── README.md
├── LICENSE
├── SECURITY.md
├── CONTRIBUTING.md
├── .editorconfig
├── .gitignore
├── .gitattributes
├── .github/
│   ├── ISSUE_TEMPLATE/
│   ├── pull_request_template.md
│   └── workflows/
├── docs/
│   ├── product/
│   ├── architecture/adr/
│   ├── implementation/
│   └── privacy/
├── fixtures/synthetic-library/
├── schemas/
├── core/
├── cli/
├── apps/android/
├── apps/windows/
└── tools/
```

The implementation directories remain provisional until the architecture spike is complete.

## Guardrails

- Never commit real audio, listening history, OAuth tokens, credentials, or personal absolute paths.
- Use synthetic artists, albums, tracks, devices, playlists, and events.
- Media fixtures must be tiny, generated, or clearly redistributable.
- Destructive tests run only inside disposable temporary roots.
- No feature may delete outside a registered Media Ecosystem root.
- Pull requests affecting identity, synchronization, deletion, or migration require explicit invariant tests.

## First pull request

**Title:** `Establish Media Ecosystem v1 product contract and repository guardrails`

Include:

- README
- v1.0.0 DoD
- roadmap
- acceptance matrix
- foundation decisions
- privacy and synthetic-fixture policies
- ADR template
- CI checks for whitespace, required foundation documents, credentials,
  personal paths, and prohibited tracked files

Do not include production application implementation.

## Second pull request

**Title:** `Prove cross-platform media and storage capabilities`

Contain isolated capability spikes and a final stack-selection ADR. Do not commit to production architecture until the required Android, Windows, playback, storage, identity, configuration, and event-model proofs pass.
