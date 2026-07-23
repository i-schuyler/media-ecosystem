# Repository Agent Instructions

These instructions apply to the entire repository.

## Product contract

- Before changing behavior, read the canonical Definition of Done, roadmap,
  acceptance matrix, and foundation decisions linked from `README.md`.
- Tie implementation work to relevant DoD sections and acceptance IDs.
- Do not silently change the product contract. Product-behavior changes require
  corresponding documentation and acceptance updates.

## Work sizing and branches

- Prefer large, coherent, reviewable slices; keep unrelated cleanup outside
  the active slice.
- Start from current `main` unless explicitly continuing an existing PR, and
  use a dedicated branch.
- Never commit directly to `main` or merge a PR unless the user explicitly
  requests it.

## GitHub CLI execution

- `gh` is authenticated in the VPS host environment, not inside the Codex
  sandbox. Run GitHub CLI commands outside the sandbox through the host or an
  approved elevated execution path.
- Do not repeatedly test or troubleshoot `gh` authentication inside the
  sandbox. Do not run `gh auth login` or modify stored authentication unless
  explicitly instructed.
- Use `gh` for PR checks, Actions logs, issues, milestones, labels, PR creation,
  and other GitHub state. Confirm results instead of assuming a push or
  workflow succeeded.

## Safety and privacy

- Never commit real audio, listening history, credentials, OAuth material,
  personal paths, runtime databases, or private synchronization state. Use
  synthetic fixtures only.
- Destructive tests must operate inside disposable registered test roots.
- Missing or unmounted storage must never be interpreted as deletion.
- Identity, synchronization, migration, deletion, trash, undo, and
  duplicate-cleanup changes require invariant and failure tests.
- Do not weaken safeguards merely to make CI pass.

## Validation and completion

- Run repository guardrails locally before pushing. Inspect the complete diff
  and tracked files.
- Push without force, then open or update a draft PR and recheck GitHub Actions.
- Leave a clean working tree and report exact validation results and unresolved
  risks.
