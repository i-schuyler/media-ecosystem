# Capability-Spike Protocol

## Purpose

Phase 1 spikes produce reproducible evidence for the highest-risk platform and
shared-core capabilities before a production technology stack is selected.
GitHub capability-spike issues and resulting ADRs must link to this protocol.

## Required report fields

Every spike report records:

- Question being tested
- Related DoD sections
- Related acceptance IDs
- Platforms and exact environment
- Candidate approach or approaches
- Preconditions
- Reproduction commands
- Success criteria
- Failure criteria
- Measurements
- Results
- Known limitations
- Security and privacy observations
- Production suitability
- Disposition: **adopt**, **reject**, **retain for comparison**, or
  **inconclusive**
- Required ADR or follow-up issue

## Rules

1. Spike code is evidence, not automatically production code, and may be
   discarded.
2. No framework, playback engine, storage API, or other component becomes
   canonical merely because it was tried first.
3. Document failures, abandoned approaches, environmental constraints, and
   inconclusive results. A failed proof must not be disguised by reducing a
   DoD requirement.
4. Make results reproducible where practical. Record commands, fixtures,
   versions, configuration, and raw measurements needed to repeat the proof.
5. Measurements identify the device model and exact environment according to
   the supported test-device matrix, without personal identifiers.
6. Select production architecture only after all required proofs are complete
   and compared through an ADR.
7. Platform-specific workarounds must not silently alter shared canonical
   identity, synchronization, conflict, or safety behavior.
8. Use only synthetic or tiny generated/clearly redistributable fixtures.
   Destructive tests stay inside disposable registered test roots.
9. Link the GitHub issue to its report, evidence, follow-up issues, and ADR.
   The ADR must distinguish measured evidence from assumptions.

## Reusable report template

```markdown
# Capability spike: <question>

- Related DoD sections:
- Related acceptance IDs:
- Platform and exact environment:
- Candidate approach(es):
- Preconditions:

## Reproduction commands

    <commands>

## Criteria

- Success:
- Failure:
- Required measurements:

## Results and measurements

<observations, measurements, and evidence links>

## Limitations, security, and privacy

- Known limitations:
- Security observations:
- Privacy observations:

## Production suitability and disposition

- Production suitability:
- Disposition: adopt | reject | retain for comparison | inconclusive
- Required ADR or follow-up issue:
```
