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

- **Initial archive:** The ignored 2026-07-24 raw ZIP SHA-256 is
  `882dd5f54d79094021b1228c92ec08e3797c341fc995b877deb8ccd4f24069e5`.
  Its allowlist, internal checksums, source/build, corrected v1 compatibility
  schema, and privacy boundary passed. See the
  [initial sanitized report](evidence/android-2026-07-24-sanitized.json).
- **Targeted archive:** The ignored 2026-07-28 raw ZIP SHA-256 is
  `593b28964854248ccb0b5177d6b27c2c26c43429b08ca3fdd8cecfc437c9d6d2`.
  Its exact seven-member allowlist, internal checksums, fixture manifest,
  build metadata, source commit, schema 1.1, and privacy boundary passed. See
  the [targeted sanitized report](evidence/android-2026-07-28-targeted-retest-sanitized.json).
- **Tooling:** Foreground media playback permissions and service type,
  MediaSession integration, automatic audio focus, becoming-noisy handling,
  wake mode, synthetic metadata, notification/session controls, and structured
  observations are implemented.
- **Screen-off threshold:** 300,000 ms (five minutes). Shorter observations are
  explicitly recorded as not meeting the threshold.
- **Host validation:** State translation, monotonic-duration rejection, bounded
  timeout logic, evidence contracts, lint, and APK packaging are covered by
  automated validation.
- **Physical controls:** Notification play/pause, lock-screen play/pause and
  metadata, hardware media-button behavior, audio-focus interruption, and
  becoming-noisy behavior were acknowledged in the initial session.
- **Screen-off result:** The targeted session completed continuous screen-off
  playback for **888,433 ms** against the required 300,000 ms minimum. The
  workflow phase was `COMPLETED`; playback remained continuous, the minimum was
  reached, and the test was explicitly completed.
- **Exit criteria:** **Satisfied** for this disposable candidate. The combined
  initial and targeted evidence covers the required screen-off and system-control
  dimensions without converting build success into physical evidence.

## Limitations, security, and privacy

- Bluetooth/hardware-button evidence is conditional on an available test
  device; absence would be recorded rather than silently passed.
- The playback candidate uses bundled synthetic assets and no network,
  personal audio, media-library scan, production queue, or production state.
- The exported diagnostic log is sanitized and excludes unrelated device or
  application information.
- This proof establishes capability on the primary Android validation device;
  it does not establish long-term production suitability or broad device
  compatibility.

## Production suitability and disposition

- **Production suitability:** Not established. Media3 1.10.1 is a disposable
  candidate, not the selected production engine.
- **Disposition:** **retain for comparison**; PB-02 evidence is complete for
  this candidate and GitHub issue #3 is closed.
- **Required follow-up:** None for issue #3. Carry this evidence into the later
  candidate-stack comparison and architecture ADR. Repeat only if a future
  candidate or material lifecycle implementation change invalidates it.
