# Microsoft Surface Book 3 physical proof protocol

## Boundary

This is the smallest combined physical session for issue #4 and the Windows
half of issue #5. The human performs every disruptive lifecycle action. The
diagnostic app must not be scripted to launch, sleep, terminate, restart, or
relaunch the computer.

Use only the synthetic, self-contained artifact from the source commit under
review. Do not browse, select, or play personal media.

## Preconditions

1. Record the reviewed source commit, CI run, artifact filename, byte size, and
   SHA-256.
2. Verify the downloaded artifact SHA-256 and unpack it outside the repository.
3. Confirm the directory contains `MediaEcosystem.WindowsProof.exe`, the
   self-contained runtime, `schemas/`, and the exact `fixtures/` corpus.
4. Ensure ordinary Windows audio output is available. Do not install a runtime,
   codec pack, certificate, package identity, or application installer.
5. Close unrelated media players so the SMTC source under observation is
   unambiguous.

## One bounded session

1. Launch `MediaEcosystem.WindowsProof.exe` manually.
2. Select **Verify packaged fixtures**. Stop if it does not show the exact six
   and a manifest SHA-256.
3. Select **Run exact six-format matrix** once. Wait for all six stable rows.
   A decoder failure must advance to the next row; do not replace or transcode
   a fixture.
4. Select **Start lifecycle / SMTC playback**.
5. Open Windows Quick Settings or the system media surface. Confirm whether
   `Synthetic Windows Lifecycle Proof` and
   `Media Ecosystem Synthetic Lab` are visible. Use the matching explicit human
   observation button.
6. In Windows system media controls, press **Pause**, verify audio/playback
   pauses, then press **Play**, verify it resumes. If a system position control
   is available, exercise it once; this dimension is optional. Do not use an
   in-app control as a substitute.
7. Confirm the UI shows automatic command-manager play and pause events.
8. Select **Mark ready for sleep** and note the displayed pre-sleep playback
   state.
9. Use Windows **Start → Power → Sleep** yourself. Allow the machine to enter
   ordinary sleep, wait at least 30 seconds after sleep is apparent, then wake
   it normally.
10. Return to the app. Confirm suspend and resume were observed, then select
    **Record wake result**. Record any audio, state, or SMTC limitation exactly
    as shown; do not retry merely to erase a failure.
11. Select **Begin app-restart checkpoint**. Close the app yourself. Do not
    terminate the computer.
12. Reopen the same executable. Confirm the checkpoint says `reopened` and the
    six matrix rows remain.
13. Select **Verify packaged fixtures**, then
    **Start lifecycle / SMTC playback**. Confirm a new playback begins. This is
    not a claim that the old playback survived process termination.
14. Select **Complete checkpoint after reopen + new playback**.
15. Select **Export verified evidence ZIP**, choose a destination outside the
    repository, and retain the required UTC filename.
16. Record the app-displayed ZIP filename, byte size, and SHA-256. Do not alter
    the raw ZIP and do not add it to Git.

## Handoff

Provide the unchanged raw ZIP and the independently recorded whole-ZIP SHA-256
for evidence review. The reviewer must verify:

- exact seven-member allowlist;
- internal `CHECKSUMS.sha256`;
- fixture manifest digest and exact six-format membership;
- evidence schema and build/source metadata;
- privacy/forbidden-field boundary;
- all per-format dimensions;
- automatic command-manager events versus human acknowledgements;
- suspend/resume, wake, and restart checkpoint observations.

Only then may a reproducible sanitized JSON report be added under `evidence/`.
Issue #4 and issue #5 remain open until that review supports their exit
criteria.
