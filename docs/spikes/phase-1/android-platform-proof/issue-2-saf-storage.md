# Capability spike: persisted Android removable-storage access

- **Question being tested:** Can the primary Android tablet retain explicit
  read/write SAF access to the user-selected removable root across process
  restart, reboot, removal/reinsertion, revocation, and explicit relink without
  interpreting unavailable storage as deletion?
- **Related DoD sections:** Identity and paths; Availability; Restore and
  migration.
- **Related acceptance IDs:** FS-01, FS-02.
- **Platform and exact environment:** Samsung Galaxy Tab S10 FE 5G, Android
  16 build `BP4A.251205.006.X528USQU9CZE9`, model `samsung SM-X528U`,
  `arm64-v8a`.
- **Candidate approach:** Official Android Storage Access Framework
  directory-tree picker, persistable URI permission, and `DocumentsContract`
  inside a disposable Java 17 proof app.
- **Preconditions:** Installable proof APK; removable SD card; system picker
  capable of presenting the intended root; synthetic marker data only.

## Reproduction

Build and APK validation commands are in the
[proof README](../../../../spikes/android-platform-proof/README.md). The app
guides root selection, immediate access, intentional process termination,
reboot/relaunch, access rechecks, guided revocation, explicit relink, export,
and optional cleanup. It flushes its evidence before intentional termination
and asks the operator only to reopen it.

## Criteria

- **Success:** Persisted access and the versioned marker survive process
  restart and reboot; removal becomes unavailable with no deletion intent;
  reinsertion restores the existing grant where supported; otherwise only an
  explicit picker selection with the matching marker relinks; intentional
  revocation is visible; cleanup touches only the owned proof directory.
- **Failure:** Raw URI or physical volume identity is exported; identity is
  inferred from name/capacity/path/volume identifier; unavailable state clears
  remembered state or generates deletion; relink guesses; cleanup can delete
  the root, siblings, or an unowned directory.
- **Required measurements:** Runtime environment, permission flags, sanitized
  root token/equality observations, every explicit state transition, marker
  validation, unavailable-not-deleted assertions, user acknowledgements,
  monotonic timing, errors, and cleanup state.

## Results and measurements

- **Archive verification:** The ignored raw ZIP SHA-256 is
  `882dd5f54d79094021b1228c92ec08e3797c341fc995b877deb8ccd4f24069e5`.
  The exact seven-member allowlist, internal checksums, fixture manifest,
  build metadata, source commit
  `a5fa2f657d63b5e89491562456744513266ba861`, corrected v1 compatibility
  schema, and privacy boundary passed. See the
  [sanitized report](evidence/android-2026-07-24-sanitized.json).
- **Tooling:** The app takes the persistable grant, stores exact URIs
  only in private application state, creates one
  `Media-Ecosystem-Phase1-Proof-v1` child and marker, implements all required
  states, and exports only sanitized evidence.
- **Host validation:** Unit tests cover the state transitions, permission
  representation, URI/volume redaction, marker validation, cleanup refusal,
  explicit relink, and the unavailable-never-deleted invariant.
- **Physical results:** Persisted read/write permission was present; the
  versioned marker was accessible at export and after a recorded reboot. The
  exported unavailable-not-deleted assertion was true. No actual unavailable
  transition, intentional process-termination checkpoint, removal/reinsertion,
  persisted-grant revocation, or explicit relink was recorded.
- **Exit criteria:** **Not fully satisfied.** Persisted reboot access is
  evidenced; removal/unavailable/restoration or relink remains unperformed.

## Limitations, security, and privacy

- Android 11 and later may restrict which storage roots a provider exposes;
  this run records only the provider behavior actually observed.
- The tablet uses a shared SIM/microSD tray that is effectively permanent in
  ordinary use. Physical removal requires case removal and a SIM-eject tool;
  this slice does not ask the user to repeat it.
- Safe future alternatives include Android system unmount/eject where
  available, persisted-permission revocation, provider-unavailability
  simulation, or a secondary device with conveniently removable storage.
  None may weaken the unavailable-never-deleted invariant.
- The proof never scans personal media or siblings. Its isolated marker is
  deterministic synthetic JSON plus a proof-session UUID and hash.
- Export contains no raw document URI, path, volume identifier, account data,
  serial, or library listing.
- Cleanup is optional until after export and fails closed without exact
  marker/session ownership evidence.

## Production suitability and disposition

- **Production suitability:** Not established. This tests an Android platform
  capability and one evidence workflow, not the final storage abstraction.
- **Disposition:** **retain for comparison** for the observed persisted reboot
  access; the complete issue remains inconclusive.
- **Required follow-up:** Do not repeat physical tray removal on this primary
  device. Evaluate one of the documented safe alternatives in a future
  focused slice, then reassess issue #2. The later architecture ADR must
  compare all completed and unresolved Phase 1 evidence.
