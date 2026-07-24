# Capability spike: Android background playback and system media controls

- **Question being tested:** Does one current official Android playback/session
  candidate remain active through background and a five-minute screen-off
  interval while exposing reliable notification, lock-screen, and available
  hardware/Bluetooth controls and observable interruption behavior?
- **Related DoD sections:** Playback; Persistent queue.
- **Related acceptance IDs:** PB-02.
- **Platform and exact environment:** Samsung Galaxy Tab S10 FE 5G, Android
  16. Runtime build/model/architecture evidence is pending.
- **Candidate approach:** AndroidX Media3 1.10.1 ExoPlayer plus
  `MediaSessionService`, used as one disposable candidate only.
- **Preconditions:** Installed proof APK, notification permission where
  required, synthetic fixture playlist, and optional media-button hardware.

## Reproduction

The app starts synthetic playback, records service/player/activity states,
loops the short fixture playlist, detects screen off/on, and measures elapsed
time with the monotonic clock. The operator backgrounds the activity, turns
the screen off for at least five minutes, returns, and exercises the available
system controls. App buttons acknowledge only actions actually performed.

## Criteria

- **Success:** Playback stays active through the documented interval;
  notification and lock-screen play/pause work; metadata is correct; available
  hardware/Bluetooth controls work; activity recreation/relaunch and service
  state are recorded; audio-focus and becoming-noisy behavior are observed
  when safely testable; no unexpected stop is hidden.
- **Failure:** Playback stops unexpectedly, system controls or metadata are
  unreliable, interruptions behave unsafely, elapsed duration is inferred from
  wall clock, or an unperformed physical action is reported as passed.
- **Required measurements:** App/candidate/source versions, exact Android
  environment, start/background/screen-off/screen-on events, monotonic
  duration, system-control acknowledgements, focus/noisy observations,
  activity/service/player state, errors, and unexpected stops.

## Results and measurements

- **Tooling:** Ready. Foreground media playback permissions and service type,
  MediaSession integration, automatic audio focus, becoming-noisy handling,
  wake mode, synthetic metadata, notification/session controls, and structured
  observations are implemented.
- **Screen-off threshold:** 300,000 ms (five minutes). Shorter observations are
  explicitly recorded as not meeting the threshold.
- **Host validation:** State translation, monotonic-duration rejection, bounded
  timeout logic, evidence contracts, lint, and APK packaging are testable on
  the host.
- **Physical results:** **Pending / not run.** No background survival or
  system-control behavior is claimed from host tests or APK assembly.
- **Exit criteria:** **Not satisfied at the tooling checkpoint.**

## Limitations, security, and privacy

- Bluetooth/hardware-button evidence is conditional on an available test
  device; absence is recorded rather than silently passed.
- The playback candidate uses bundled synthetic assets and no network,
  personal audio, media-library scan, production queue, or production state.
- The exported diagnostic log is sanitized and excludes unrelated device or
  application information.

## Production suitability and disposition

- **Production suitability:** Not established. Media3 1.10.1 is a disposable
  candidate, not the selected production engine.
- **Disposition:** **inconclusive** pending the physical lifecycle session.
- **Required follow-up:** Verify and sanitize the exported ZIP, evaluate issue
  #3 against unchanged PB-02 criteria, and retain results for the later
  candidate-comparison ADR.
