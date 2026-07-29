# Capability spike: Windows playback lifecycle and system media controls

- **Question:** Can the disposable `Windows.Media.Playback.MediaPlayer`
  candidate play one verified synthetic fixture, expose automatic Windows
  System Media Transport Controls, respond to real system commands, and recover
  cleanly across sleep/wake and one bounded app restart?
- **Related DoD sections:** Playback; Persistent queue.
- **Related acceptance IDs:** PB-05; PB-01 is reported separately.
- **Platform:** Microsoft Surface Book 3 on Windows 11; exact physical
  environment will come from the sanitized evidence.
- **Candidate:** .NET SDK 8.0.423, .NET runtime 8.0.29,
  `net8.0-windows10.0.19041.0`, Windows SDK .NET reference 10.0.26100.84,
  Windows Forms, `MediaPlayer`, `MediaPlaybackItem`, and the enabled automatic
  `MediaPlaybackCommandManager`.
- **Current disposition:** **inconclusive — tooling prepared, physical session
  not run**.

## Preconditions and reproduction

1. Obtain the verified self-contained `win-x64` tooling artifact for the source
   commit under review.
2. Unpack it outside the repository on the Surface Book 3.
3. Follow
   [PHYSICAL_SESSION_PROTOCOL.md](PHYSICAL_SESSION_PROTOCOL.md) without
   substituting in-app clicks for Windows system commands.
4. Export the raw evidence ZIP outside Git and independently verify it before
   importing any sanitized report.

Source reproduction and package verification commands are in the
[proof README](../../../../spikes/windows-platform-proof/README.md) and exact
versions are in
[TOOLCHAIN.md](../../../../spikes/windows-platform-proof/TOOLCHAIN.md).

## Success and failure criteria

PB-05 can pass this spike only when the physical evidence records:

- verified `mp3-v0` lifecycle fixture and playback began;
- automatic SMTC candidate state became active;
- a human explicitly confirmed the applied synthetic title and artist visible;
- automatic `PlayReceived` and `PauseReceived` events each affected playback;
- optional position command separately recorded if exercised;
- playback state immediately before sleep;
- application-observed suspend and resume events;
- playback and SMTC state recorded after wake;
- one private bounded checkpoint, clean reopen, and new playback after reopen;
- no exported private checkpoint path;
- all failures and limitations.

Failure or absence is recorded as failed/inconclusive; the criterion is not
weakened. A human acknowledgement never becomes an automatic event. Applied
system display metadata never becomes extracted file metadata.

## Tooling result

The candidate compiles with zero warnings and supplies:

- a separate looping lifecycle player;
- the enabled automatic command-manager path;
- automatic play, pause, and optional position observations;
- manual metadata-visible / metadata-not-visible acknowledgements;
- passive `PowerModeChanged` suspend/resume observations;
- explicit pre-sleep and post-wake capture;
- atomic private checkpoint plus one prior generation;
- guided close/reopen/new-playback completion;
- partial/failure-preserving export.

This establishes only that the code and Windows SDK surface are buildable. It
does not establish that an unpackaged desktop process actually appears in SMTC
or behaves correctly on the Surface Book 3.

## Limitations, security, and privacy

- The app does not initiate sleep, wake, shutdown, restart, termination, or
  relaunch.
- It does not claim audio survives process termination.
- The restart checkpoint is not a production queue.
- The private checkpoint path, user name, hostname, accounts, serial/machine/
  volume identifiers, environment variables, personal filenames, libraries,
  credentials, and signing material are excluded.
- The manually applied synthetic SMTC title/artist are exported separately
  from optional metadata extracted through Windows file properties.

## Production suitability

Not established. The candidate remains **retain for comparison** only after
physical observations are available. Issue #4 stays open and the Phase 1 stack
ADR remains pending.
