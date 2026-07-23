# Capability spike: identity-sidecar round trips (#7)

- **Proof question:** Can an experimental human-readable sidecar preserve
  Track, Folder, and File Instance IDs across modeled operations without
  deriving or guessing identity?
- **Related DoD sections:** Identity and paths; Human-readable and AI-friendly
  interfaces; Duplicate detection; File operations.
- **Related acceptance IDs:** ID-01, ID-02, ID-03, PL-01.
- **Platform and exact environment:** VPS; Linux 5.15.0-185-generic x86_64;
  ext2/ext3-family filesystem label; CPython 3.12.13; 2026-07-23. Target
  devices were not executed.
- **Candidate approach:** Versioned UTF-8 JSON with UUID identifiers, canonical
  logical paths, device binding for file instances, optional expected SHA-256
  evidence, preserved extension and unknown fields, duplicate-key rejection,
  and same-directory atomic replace.
- **Preconditions:** Registered disposable roots; synthetic identifiers and
  non-media bytes only.

## Reproduction commands

```sh
python3 spikes/shared-core-foundations/scripts/harness.py verify --seeds 7,20260723
```

## Criteria

- **Success:** All three ID types round-trip exactly; move/rename do not mutate
  IDs; intended copies share Track ID but not File Instance ID; duplicate and
  malformed evidence is explicit; missing evidence stays unidentified; unknown
  fields survive; writes cannot escape the registered root; replacement needs
  explicit authorization.
- **Failure:** Identity is inferred from mutable properties, malformed data is
  accepted, a duplicate silently merges, an interrupted write replaces valid
  evidence with partial data, or any target escapes the test root.
- **Required measurements:** Deterministic bytes, cases passed, fault point,
  and target filesystem/runtime behavior.

## Results and measurements

- Evidence level: **Proven on VPS** for nine sidecar invariant/failure tests;
  **harness ready but target-device run pending** for filesystem behavior.
- File and folder documents serialized deterministically and round-tripped all
  IDs without mutation.
- Rename/move and changed expected content preserved immutable IDs. Two
  intended instances on different synthetic Device IDs shared one Track ID and
  had distinct File Instance IDs without being classified as redundant. A
  second instance on the same Device ID was surfaced as a duplicate Track ID,
  while repeated File Instance ID reuse failed explicitly.
- Unknown extension and top-level fields survived read-modify-write.
- Invalid UUIDs, duplicate JSON keys, malformed/missing evidence, unsafe paths,
  and a symlink-parent escape failed explicitly. Missing evidence returned an
  unidentified state rather than a similarity guess.
- A fault after temporary-file `fsync` left the prior sidecar intact and
  cleaned temporary state. Explicit replacement retained Track ID only with a
  new File Instance ID and explicit authorization.
- The versioned candidate is documented in
  [`EXPERIMENTAL_SIDECAR_FORMAT.md`](../../../../spikes/shared-core-foundations/EXPERIMENTAL_SIDECAR_FORMAT.md).

## Limitations, security, and privacy

- **Known limitations:** Linux `os.replace` and directory synchronization do
  not prove Android SD-card or Windows durability. This proof does not exercise
  embedded audio tags, every required format, real scan/reconciliation, or a
  production database. Windows directory flush is not implemented by this
  candidate. Duplicate Track ID detection surfaces review; it does not choose
  a retained file.
- **Security observations:** Duplicate keys and unsupported versions fail
  closed; parent resolution includes symlink containment; no operation accepts
  an outside target; malformed evidence never creates an identity.
- **Privacy observations:** All UUIDs, names, hashes, and content are synthetic.

## Production suitability and disposition

- **Production suitability:** Not established; the schema is deliberately
  experimental and disposable.
- **Disposition:** **retain for comparison**.
- **Required target-device follow-up:** Run the unified suite on Android
  Termux and Windows 11, record atomic-write and filesystem observations, and
  separately prove safe embedded-tag behavior per required audio format before
  a sidecar/embedded-ID ADR.
- **ADR implications:** A later identity ADR must decide schema evolution,
  embedded-tag policy, directory durability, duplicate review, and target
  filesystem behavior. This report does not make those decisions.

Issue #7 is partially evidenced and remains open while target filesystem
behavior and the draft evidence are pending.
