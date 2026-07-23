# Capability spike: portable root-relative path normalization (#6)

- **Proof question:** Can one falsifiable logical-path contract produce
  deterministic root-relative paths while rejecting shared physical roots,
  traversal, and cross-platform name hazards?
- **Related DoD sections:** Identity and paths; File operations; Restore and
  migration.
- **Related acceptance IDs:** FS-01, FS-02, OP-01.
- **Platform and exact environment:** VPS; Linux 5.15.0-185-generic x86_64;
  ext2/ext3-family filesystem label reported by `stat`; CPython 3.12.13; tested
  2026-07-23. Android and Windows were not executed.
- **Candidate approach:** NFC display paths with `/` separators, a separate
  NFC-plus-casefold collision key, conservative invalid-name and UTF-8 byte
  limits, and a device-local registered-root resolver.
- **Preconditions:** Clean repository checkout; synthetic corpus only; no
  personal roots or media.

## Reproduction commands

```sh
python3 spikes/shared-core-foundations/scripts/harness.py paths
python3 spikes/shared-core-foundations/scripts/harness.py verify --seeds 7,20260723
```

Run the path command unchanged through the harness on Android Termux; on
Windows use `py -3` as documented in the
[spike README](../../../../spikes/shared-core-foundations/README.md).

## Criteria

- **Success:** Every positive vector yields its recorded display path and
  comparison key; every unsafe vector fails; collisions remain explicit;
  relink preserves logical paths; a missing root is unavailable rather than a
  deletion signal.
- **Failure:** Any absolute/traversal/invalid path is accepted, an expected
  portable path changes nondeterministically, a collision selects a winner, or
  relink mutates the shared logical path.
- **Required measurements:** Vector count and result, normalization/collision
  observations, exact runtime/filesystem, and per-target result.

## Results and measurements

- Evidence level: **Proven on VPS** for deterministic Python behavior and
  registered-root containment; **simulated only** for Windows-shaped input;
  **harness ready but target-device run pending** for Android and Windows.
- All 16 positive/negative vectors and two collision sets passed.
- Windows separators normalized to `/`; POSIX absolute, drive-letter, UNC,
  dot traversal, empty component, reserved-name, trailing dot/space, and
  invalid-character inputs failed closed.
- Decomposed Unicode normalized to NFC. Display case was preserved while the
  comparison key exposed case-only and normalization collisions.
- The 255-byte component and 1,024-byte path policies passed boundary tests.
- Rename, move, physical-root relink, and missing-root tests passed without
  changing or deleting logical state.
- The executable contract and machine-readable expectations are documented in
  [`EXPERIMENTAL_PATH_CONTRACT.md`](../../../../spikes/shared-core-foundations/EXPERIMENTAL_PATH_CONTRACT.md)
  and [`path-vectors.json`](../../../../spikes/shared-core-foundations/fixtures/path-vectors.json).

## Limitations, security, and privacy

- **Known limitations:** Linux did not prove actual Android removable-storage
  or Windows filename/API behavior. Unicode casefold is a conservative
  experimental comparison, not a measured NTFS or Android filesystem rule.
  Exact long-path, normalization-on-disk, Explorer, Storage Access Framework,
  removal, reinsertion, and relink behavior remain unmeasured.
- **Security observations:** Absolute roots, traversal, UNC/drive injection,
  invalid names, and containment escape fail closed. A missing registered root
  raises an unavailable result and never emits deletion intent.
- **Privacy observations:** Corpus names and roots are synthetic and temporary;
  no physical root enters shared fixtures or committed output.

## Production suitability and disposition

- **Production suitability:** Not established. The model is useful input to a
  later storage/path decision only after both target runs.
- **Disposition:** **retain for comparison**.
- **Required target-device follow-up:** Run `paths` and the unified suite on the
  Galaxy Tab S10 FE 5G in Termux and Surface Book 3 on Windows 11; add actual
  filesystem creation, removable/unavailable root, reinsertion, and relink
  observations from issues #2 and platform storage proofs.
- **ADR implications:** A later storage or stack ADR must separate shared
  logical paths from device-local roots and compare measured target filesystem
  semantics. No ADR is created by this result.

Issue #6 is partially evidenced and remains open pending both target-device
runs.

