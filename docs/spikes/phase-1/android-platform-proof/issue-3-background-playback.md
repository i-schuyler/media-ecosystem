# Capability spike: Android background playback and system media controls

- **Question being tested:** Does one current official Android playback/session
  candidate remain active through background and a five-minute screen-off
  interval while exposing reliable notification, lock-screen, and available
  hardware/Bluetooth controls and observable interruption behavior?
- **Related DoD sections:** Playback; Persistent queue.
- **Related acceptance IDs:** PB-02.
- **Platform and exact environment:** Samsung Galaxy Tab S10 FE 5G, Android
  16 build `BP4A.251205.006.X528USQU9CZE9`, model `samsung SM-X528U`,
  `arm64-v8a`.
- **Candidate approach:** AndroidX Media3 1.10.1 ExoPlayer plus
  `MediaSessionService`, used as one disposable candidate only.
- **Preconditions:** Installed proof APK, notification permission where
  required, synthetic fixture playlist, and optional media-button hardware.

## Reproduction

The app starts synthetic playback, records service/player/activity states,
loops the short fixture playlist, detects screen off/on, and measures elapsed
time with the monotonic clock. The hardened retest workflow records distinct
test-started, playback-ready, screen-off-playing, minimum-reached,
controls-exercised, and completed states. Completion is refused until the
300,000 ms minimum is reached.

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

- **Archive verification:** The ignored raw ZIP SHA-256 is
  `882dd5f54d79094021b1228c92ec08e3797c341fc995b877deb8ccd4f24069e5`;
  its allowlist, internal checksums, source/build, corrected v1 compatibility
  schema, and privacy boundary passed. See the
  [sanitized report](evidence/android-2026-07-24-sanitized.json).
- **Tooling:** Foreground media playback permissions and service type,
  MediaSession integration, automatic audio focus, becoming-noisy handling,
  wake mode, synthetic metadata, notification/session controls, and structured
  observations are implemented.
- **Screen-off threshold:** 300,000 ms (five minutes). Shorter observations are
  explicitly recorded as not meeting the threshold.
- **Host validation:** State translation, monotonic-duration rejection, bounded
  timeout logic, evidence contracts, lint, and APK packaging are testable on
  the host.
- **Physical results:** Notification play/pause, lock-screen play/pause and
  metadata, hardware media-button behavior, audio-focus interruption, and
  becoming-noisy behavior were acknowledged. Playback was active at screen-off
  and on return, but the monotonic interval was only **137 ms**, not 300,000
  ms.
- **Root cause:** The original workflow recorded the brief Android off/on
  broadcast correctly but had no explicit playback-ready, minimum-duration, or
  completion gate, so a short event could advance the guided workflow.
- **Fix:** The retest state machine makes readiness and the five-minute
  threshold visible, records continuous-playback failure, and refuses
  completion until the minimum is reached.
- **Exit criteria:** **Not satisfied.** The media-control observations are
  retained; only the five-minute screen-off interval requires repetition.

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
- **Disposition:** **retain for comparison** for the acknowledged controls;
  overall PB-02 remains inconclusive.
- **Required follow-up:** Run only the hardened five-minute screen-off retest
  and export the resulting evidence. Do not repeat already acknowledged
  control/interruption observations unless a future tooling change invalidates
  them.
