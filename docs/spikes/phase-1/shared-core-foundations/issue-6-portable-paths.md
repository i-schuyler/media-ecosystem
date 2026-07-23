# Capability spike: portable root-relative path normalization (#6)

- **Proof question:** Can one falsifiable logical-path contract produce
  deterministic root-relative paths while rejecting shared physical roots,
  traversal, and cross-platform name hazards?
- **Related DoD sections:** Identity and paths; File operations; Restore and
  migration.
- **Related acceptance IDs:** FS-01, FS-02, OP-01.
- **Platform and exact environment:** VPS baseline as previously recorded;
  Samsung Galaxy Tab S10 FE 5G, Android 16 build
  `BP4A.251205.006.X528USQU9CZE9`, aarch64, Python 3.14.6 in Termux, F2FS
  private storage and an Android-exposed portable-SD FUSE mount; and Microsoft
  Surface Book 3, Windows 11 Pro 25H2 build `26200.8894`, 64-bit NTFS, CPython
  3.14.3. Target runs were recorded on 2026-07-22/23 local time.
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

- Evidence level: **Proven on the reference model and executed on both primary
  target devices** for deterministic harness behavior; measured on the
  Android-exposed FUSE mount and Windows internal NTFS for filesystem
  observations.
- All 16 positive/negative vectors and two collision sets passed.
- Windows separators normalized to `/`; POSIX absolute, drive-letter, UNC,
  dot traversal, empty component, reserved-name, trailing dot/space, and
  invalid-character inputs failed closed.
- Decomposed Unicode normalized to NFC. Display case was preserved while the
  comparison key exposed case-only and normalization collisions.
- The 255-byte component and 1,024-byte path policies passed boundary tests.
- Rename, move, physical-root relink, and missing-root tests passed without
  changing or deleting logical state.
- The Android completed-run summary records all 35 shared-core tests passed.
  On the portable-SD FUSE mount, ordinary rename passed, a unique disposable
  child could be created and removed, and case-distinct names could not coexist.
  This actual mount observation informs collision detection but does not turn
  symlink support into a canonical path requirement.
- The Windows final run recorded 46 tests with 45 passed and one
  Windows-directory-symlink test skipped. All path vectors passed. In a
  separate non-elevated NTFS probe, case-distinct names did not coexist,
  NFC-equivalent composed/decomposed names did coexist, trailing dots/spaces
  were removed by the observed path API, a 255-character component succeeded,
  a 256-character component failed, and a 323-character complete path
  succeeded.
- The exact Windows environment, raw provenance, and scope are in the
  [Windows evidence report](windows-internal-storage.md).
- The exact Android storage environment, probe, and interpretation are in the
  [portable-SD evidence report](android-portable-sd-storage.md).
- The executable contract and machine-readable expectations are documented in
  [`EXPERIMENTAL_PATH_CONTRACT.md`](../../../../spikes/shared-core-foundations/EXPERIMENTAL_PATH_CONTRACT.md)
  and [`path-vectors.json`](../../../../spikes/shared-core-foundations/fixtures/path-vectors.json).

## Limitations, security, and privacy

- **Known limitations:** Unicode casefold remains a conservative experimental
  comparison. One NTFS/CPython path observation does not generalize to every
  Windows API or filesystem. Explorer, app-level Storage Access Framework
  persistence, removal, reinsertion, and relink behavior remain unmeasured.
  Raw Termux access is not SAF evidence.
- **Security observations:** Absolute roots, traversal, UNC/drive injection,
  invalid names, and containment escape fail closed. A missing registered root
  raises an unavailable result and never emits deletion intent.
- **Privacy observations:** Corpus names and roots are synthetic and temporary;
  no physical root enters shared fixtures or committed output.

## Production suitability and disposition

- **Production suitability:** Not established. The cross-platform model is
  useful input to a later storage/path decision, while app-level Android root
  lifecycle and Windows Explorer behavior remain separate work.
- **Disposition:** **retain for comparison**.
- **Required target-device follow-up:** Separately add app-level Android SAF,
  removable/unavailable root, reinsertion, and relink observations through the
  Android root-access issue.
- **ADR implications:** A later storage or stack ADR must separate shared
  logical paths from device-local roots and compare measured target filesystem
  semantics. No ADR is created by this result.

Issue #6's existing exit criteria are evidenced across the recorded Android
and Windows executions: the same valid logical representation round-trips,
unsafe traversal/root escape fails, and missing roots remain unavailable
rather than deleted. The issue may close; application storage lifecycle and
production policy remain separate Phase 1 work.
