# Windows platform proof evidence index

## Status

The disposable tooling for issue #4 and the Windows half of issue #5 is
prepared for review. Physical evidence has **not** been collected. PB-01 and
PB-05 remain unproven on Windows, both issues remain open, and no production
technology has been selected.

## Contract

- DoD areas: Required formats; Playback; Persistent queue.
- Acceptance IDs: PB-01 and PB-05.
- Primary device: Microsoft Surface Book 3 on Windows 11.
- Candidate: .NET 8 Windows Forms diagnostic using
  `Windows.Media.Playback.MediaPlayer` and automatic SMTC integration.
- Source workspace:
  [`spikes/windows-platform-proof/`](../../../../spikes/windows-platform-proof/README.md)
- Implementation plan: [PLAN.md](PLAN.md)
- Physical protocol:
  [PHYSICAL_SESSION_PROTOCOL.md](PHYSICAL_SESSION_PROTOCOL.md)

## Reports

- [Issue #4 — Windows playback lifecycle and SMTC](issue-4-windows-playback-smtc.md)
- [Issue #5 — exact six-format Windows matrix](issue-5-windows-formats.md)
- [Sanitized evidence reservation](evidence/README.md)

These reports state tooling coverage and the exact pending observations. They do
not contain placeholder pass claims.

## Evidence boundary

The future raw ZIP remains outside Git. It must contain the exact seven-member
allowlist, pass schema/privacy/internal-checksum validation, reopen byte-for-byte,
and have its whole-archive SHA-256 recorded before a sanitized report is
imported. Build or CI success is not physical evidence.
