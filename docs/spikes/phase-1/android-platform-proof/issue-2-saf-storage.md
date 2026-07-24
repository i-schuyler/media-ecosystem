# Capability spike: persisted Android removable-storage access

- **Question being tested:** Can the primary Android tablet retain explicit
  read/write SAF access to the user-selected removable root across process
  restart, reboot, removal/reinsertion, revocation, and explicit relink without
  interpreting unavailable storage as deletion?
- **Related DoD sections:** Identity and paths; Availability; Restore and
  migration.
- **Related acceptance IDs:** FS-01, FS-02.
- **Platform and exact environment:** Samsung Galaxy Tab S10 FE 5G, Android
  16. The app records exact release/build/model/architecture at runtime;
  physical proof environment is pending.
- **Candidate approach:** Official Android Storage Access Framework
  directory-tree picker, persistable URI permission, and `DocumentsContract`
  inside a disposable Java 17 proof app.
- **Preconditions:** Installable proof APK; removable SD card; system picker
  capable of presenting the intended root; synthetic marker data only.

## Reproduction

Build and APK validation commands are in the
[proof README](../../../../spikes/android-platform-proof/README.md). The app
guides root selection, immediate access, intentional process termination,
reboot/relaunch, safe eject/removal, unavailable observation, reinsertion,
guided revocation, explicit relink, export, and optional cleanup. It flushes
its evidence before intentional termination and asks the operator only to
reopen it.

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

- **Tooling:** Ready. The app takes the persistable grant, stores exact URIs
  only in private application state, creates one
  `Media-Ecosystem-Phase1-Proof-v1` child and marker, implements all required
  states, and exports only sanitized evidence.
- **Host validation:** Unit tests cover the state transitions, permission
  representation, URI/volume redaction, marker validation, cleanup refusal,
  explicit relink, and the unavailable-never-deleted invariant.
- **Physical results:** **Pending / not run.** No restart, reboot, removable
  media, provider, revocation, or relink behavior is claimed from the build.
- **Exit criteria:** **Not satisfied at the tooling checkpoint.**

## Limitations, security, and privacy

- Android 11 and later may restrict which storage roots a provider exposes;
  the actual primary-tablet picker result must be observed.
- The proof never scans personal media or siblings. Its isolated marker is
  deterministic synthetic JSON plus a proof-session UUID and hash.
- Export contains no raw document URI, path, volume identifier, account data,
  serial, or library listing.
- Cleanup is optional until after export and fails closed without exact
  marker/session ownership evidence.

## Production suitability and disposition

- **Production suitability:** Not established. This tests an Android platform
  capability and one evidence workflow, not the final storage abstraction.
- **Disposition:** **inconclusive** until physical evidence is received; then
  retain measured results for comparison.
- **Required follow-up:** Run the guided physical sequence, verify the ZIP,
  commit only sanitized results, and evaluate issue #2 against its unchanged
  exit criteria. The later architecture ADR must compare all Phase 1 evidence.
