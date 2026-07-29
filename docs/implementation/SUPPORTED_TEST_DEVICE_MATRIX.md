# Supported Test-Device Matrix

## Scope

This matrix defines the hardware and operating-system environments used to
validate Media Ecosystem v1.0.0. A **supported platform** has completed the
applicable required tests on a device listed here with recorded evidence. An
**untested compatible platform** may run the software, but has no v1 support
claim until it is added through an explicit contract update and validation.

Success on one listed device does not establish compatibility with every
Android or Windows device. v1.0.0 release claims are limited to the exact
validation matrix documented for the release.

## Primary validation devices

| Platform | Device | Baseline environment | Storage and diagnostics |
|---|---|---|---|
| Android | Samsung Galaxy Tab S10 FE 5G | Android 16 | Removable microSD media storage; Termux available for development and diagnostics |
| Windows | Microsoft Surface Book 3 | Windows 11 | Windows-managed media roots and removable or unavailable storage where reproducible |

### Recorded Phase 1 environment evidence

The 2026-07-22 Android shared-core run records Android 16 build
`BP4A.251205.006.X528USQU9CZE9`, aarch64, kernel
`6.6.102-android15-8-abX528USQU9CZE9-4k`, and Python 3.14.6 in Termux. It covers
Termux-private F2FS storage and raw-path access through the Android-exposed
portable-SD FUSE mount; see the
[evidence index](../spikes/phase-1/shared-core-foundations/README.md). Python
3.14.6 compatibility is an experimental observation and does not change the
production runtime contract. Raw-path access does not establish persisted
application access through the Storage Access Framework.

The disposable
[Android platform proof](../../spikes/android-platform-proof/README.md) is
collecting app-level SAF, background playback/system controls, and
required-format evidence on this exact tablet. Its
[evidence index](../spikes/phase-1/android-platform-proof/README.md) records the
verified 2026-07-24 initial run and 2026-07-28 targeted retest. Continuous
screen-off playback completed for 888,433 ms against the 300,000 ms minimum,
and corrected WAV open/prepare/start, advancement, seek, duration, and natural
end passed without requiring optional metadata. All six active formats pass on
Android. Issue #2 still lacks an actual unavailable/removal/relink transition;
build success alone does not broaden support or satisfy another physical row.

The 2026-07-23 Windows shared-core run records Microsoft Surface Book 3,
Windows 11 Pro 25H2 version `10.0.26200` / build `26200.8894`, 64-bit NTFS,
Windows PowerShell Desktop 5.1.26100.8894, and CPython 3.14.3. It covers the
shared-core reference harness, a non-elevated internal-NTFS storage probe,
bounded SHA-256/resource measurements, and automated cancellation; see the
[Windows evidence report](../spikes/phase-1/shared-core-foundations/windows-internal-storage.md).
It does not establish Windows playback, Explorer monitoring, removable-media
failure handling, or sudden-power-loss durability. Python remains disposable
evidence tooling, not a production requirement.

The disposable
[Windows platform proof](../../spikes/windows-platform-proof/README.md) prepares
the issue #4 and Windows issue #5 tooling for this exact device. The pinned
.NET 8 `MediaPlayer` candidate, exact shared six-fixture verification, bounded
format runner, automatic command-manager lifecycle guide, sleep/wake observer,
private restart checkpoint, sanitized evidence export, and self-contained
`win-x64` artifact are prepared. No physical Windows playback, decoder, SMTC,
sleep/wake, restart, or evidence-ZIP result has run; tooling success does not
satisfy PB-01 or PB-05.

Do not record serial numbers, personal paths, account names, device names, or
other personal identifiers in spike reports.

### Android required tests

| Area | Required proof |
|---|---|
| Storage access | Persisted access to the selected media root |
| Removable storage | microSD removal, unavailable state, reinsertion, and safe relink |
| Playback lifecycle | Background playback, screen-off playback, app restart, and device reboot |
| System integration | Lock-screen controls and media-button controls |
| Failure conditions | Network loss and storage pressure |
| Formats | MP3 V0, MP3 320, FLAC, AAC, Ogg Vorbis, and WAV |

### Windows required tests

| Area | Required proof |
|---|---|
| Storage integration | Media-root monitoring and Explorer integration |
| System integration | Windows media controls |
| Playback lifecycle | Sleep and wake, plus app restart |
| Failure conditions | Network loss and removable or unavailable storage behavior where reproducible |
| Formats | MP3 V0, MP3 320, FLAC, AAC, Ogg Vorbis, and WAV |

## Evidence rules

- Every spike report records the device model, exact OS build/version,
  candidate application or spike version, relevant dependency versions, and
  test date. Use a synthetic device alias if a stable identifier is needed.
- Record application versions as an immutable commit SHA plus any package or
  build identifier used on the device.
- Results apply only to the recorded environment. OS, dependency, firmware,
  or hardware changes may require rerunning affected proofs.
- Add a future device by updating this matrix, identifying which existing
  support claim it extends, linking reproducible evidence, and reviewing the
  corresponding DoD and acceptance impact. Do not silently broaden the
  release contract.

## Fixture rules

| Test category | Permitted fixture |
|---|---|
| Path normalization, identity, snapshots, event merging, root monitoring, storage removal, and recovery | Synthetic directories and non-media files |
| Playback, seeking, duration/metadata handling, media controls, and format compatibility | Tiny generated or clearly redistributable audio fixtures in each required format |
| CI and repository guardrails | Synthetic files only; audio only when the fixture policy explicitly permits it |

Real personal media is never committed, copied into fixtures, or required for
CI. Any manual device-only observation involving private data must be replaced
with synthetic or redistributable evidence before it can support a release
claim.

### Primary Android removable-media constraint

The Galaxy Tab S10 FE 5G uses a shared SIM/microSD tray that is effectively
permanent in ordinary use: physical removal requires removing the case, using a
SIM-eject tool, and extracting the tray. The 2026-07-24 proof therefore records
removal/reinsertion as unperformed, and this slice does not ask for another
physical removal. Future evidence may use Android system unmount/eject where
safely available, persisted-permission revocation, provider-unavailability
simulation, or a secondary device with conveniently removable storage.
Regardless of method, unavailable or revoked storage must never become
deletion intent.
