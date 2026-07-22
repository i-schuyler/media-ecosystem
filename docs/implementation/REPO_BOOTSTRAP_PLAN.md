# Repository Bootstrap Plan

## Repository recommendation

- **Name:** `media-ecosystem`
- **Visibility:** Public
- **Posture:** Low-attention experimental pre-alpha
- **License:** Apache-2.0
- **Runtime data:** Local databases plus private Google Drive metadata area
- **Second private metadata repository:** Not part of v1.0.0

## Current foundation structure

The repository contains the Phase 0 contract, contribution guidance, planning
process, and guardrails. Application directories remain intentionally absent.

```text
media-ecosystem/
├── README.md
├── AGENTS.md
├── LICENSE
├── SECURITY.md
├── CONTRIBUTING.md
├── .editorconfig
├── .gitignore
├── .gitattributes
├── .github/
│   ├── ISSUE_TEMPLATE/
│   │   └── capability-spike.yml
│   ├── pull_request_template.md
│   ├── scripts/check-foundation.sh
│   └── workflows/
├── docs/
│   ├── product/
│   ├── architecture/adr/
│   ├── implementation/
│   │   ├── CAPABILITY_SPIKE_PROTOCOL.md
│   │   ├── PHASE_1_CAPABILITY_ISSUES.md
│   │   └── SUPPORTED_TEST_DEVICE_MATRIX.md
│   └── privacy/
```

Future fixture, schema, core, CLI, application, and tooling paths are created
only when their Phase 1 evidence or later implementation slice needs them. The
implementation directories and production stack remain undecided until the
capability proofs and stack-selection ADR are complete.

## Guardrails

- Never commit real audio, listening history, OAuth tokens, credentials, or personal absolute paths.
- Use synthetic artists, albums, tracks, devices, playlists, and events.
- Media fixtures must be tiny, generated, or clearly redistributable.
- Destructive tests run only inside disposable temporary roots.
- No feature may delete outside a registered Media Ecosystem root.
- Pull requests affecting identity, synchronization, deletion, or migration require explicit invariant tests.

## Foundation baseline

**Title:** `Establish Media Ecosystem v1 product contract and repository guardrails`

The merged foundation baseline includes:

- README
- v1.0.0 DoD
- roadmap
- acceptance matrix
- foundation decisions
- privacy and synthetic-fixture policies
- ADR template
- CI checks for whitespace, required foundation documents, credentials,
  personal paths, and prohibited tracked files

It does not include production application implementation.

## Phase 0 completion slice

The completion slice adds Apache-2.0 licensing, durable agent guidance, the
supported-device matrix, capability-spike protocol, canonical Phase 1 issue
catalog and issue form, aligned guardrails, and corresponding GitHub planning
objects.

## Phase 1 work

Phase 1 contains isolated capability spikes followed by a stack-comparison
ADR. Do not commit to production architecture until the required Android,
Windows, playback, storage, identity, configuration, durability, event-model,
and hashing proofs are complete.
