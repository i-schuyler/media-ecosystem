# Capability spike: idempotent offline event merging (#9)

- **Proof question:** Can an executable reference model converge under duplicate
  and permuted delivery, incorrect wall clocks, concurrent devices, causal
  updates, and unresolved destructive conflicts?
- **Related DoD sections:** Listening statistics; Metadata synchronization;
  Conflict behavior.
- **Related acceptance IDs:** STAT-02, STAT-03, SY-01, SY-02.
- **Platform and exact environment:** VPS; Linux 5.15.0-185-generic x86_64;
  CPython 3.12.13; 2026-07-23. This is a deterministic reference model, not a
  platform sync transport.
- **Candidate approach:** Versioned JSON-like events with unique Event and
  Device UUIDs, device-local sequence ownership, optional causal parents,
  additive commutative events, causal frontiers for rating/resume, explicit
  unresolved conflicts, and an idempotency set carried through compaction.
- **Preconditions:** Synthetic deterministic event corpus; no network or cloud
  transport; recorded randomized seeds.

## Reproduction commands

```sh
python3 spikes/shared-core-foundations/scripts/harness.py verify --seeds 7,20260723
python3 spikes/shared-core-foundations/scripts/harness.py events --seed 7 --iterations 100
python3 spikes/shared-core-foundations/scripts/harness.py events --seed 20260723 --iterations 100
```

## Criteria

- **Success:** Duplicate imports are idempotent; supported commutative state is
  invariant under delivery order; four unique one-minute increments total 240
  seconds; wall clocks do not choose causal winners; gaps and sequence reuse are
  detected; concurrent device contributions survive; unresolved destructive
  and concurrent update cases remain explicit; compaction plus remaining events
  matches one-pass state.
- **Failure:** Any permutation changes supported state, a duplicate increments
  state twice, display time overrides causal evidence, a sequence collision is
  accepted, or an unsupported conflict is silently guessed.
- **Required measurements:** Corpus and schema version, event count, recorded
  seeds/iterations, state assertions, duplicate/compaction results, and failure
  observations.

## Results and measurements

- Evidence level: **Proven on VPS** for the issue's executable reference-model
  criteria. Production synchronization remains out of scope.
- The 15-event corpus includes two offline devices and every modeled event
  class: four additive time increments, session-qualified play counts, ratings,
  resume positions, playlist additions, and a destructive-intent placeholder.
- Ten event tests passed. The unified suite ran 20 permutations for each of
  seeds `7` and `20260723`; dedicated runs added 100 permutations per seed, for
  200 explicitly recorded additional delivery orders.
- Four distinct 60-second events produced exactly 240 seconds. Two qualifying
  device sessions produced two plays. Reimporting every event did not change
  either result, and a second Event ID for the same qualified device session did
  not double-count it.
- Intentionally incorrect display timestamps did not affect causality. A rating
  event causally following two concurrent ratings reproducibly produced four
  stars despite its earlier display time. The analogous completed-meaningful
  resume event produced 250 seconds.
- Delivery with a missing device sequence reported the exact gap. Reuse of one
  device sequence by a different Event ID failed as an impossible regression.
- Concurrent ratings/resume values remained `review_required` until causally
  followed. Both playlist additions survived. Destructive intent always
  remained review-required.
- A compacted first segment plus remaining events reproduced the one-pass state;
  replaying an already compacted event did not apply it again. Corrupt,
  malformed, unknown-version, causal-cycle, missing-parent, and collision paths
  fail closed in the model/tests.

## Product-rule gaps deliberately not invented

- The producer currently supplies whether a resume session is completed and
  meaningful; the DoD does not yet define every cross-device checkpoint or
  causal-context encoding rule.
- Concurrent ratings and resume positions without a causal relationship stay
  unresolved. The harness does not introduce a timestamp or UUID tie-breaker.
- The compacted proof retains Event ID hashes, sequence ownership, causal index,
  session deduplication keys, and update frontiers. It demonstrates equivalence,
  not a final bounded-retention or garbage-collection policy.
- Destructive intent is a placeholder only; no production operation semantics
  or compensating-event design is selected.

## Limitations, security, and privacy

- **Known limitations:** No Google Drive, network delivery, production journal,
  schema migration, cryptographic event authentication, unbounded history, or
  target runtime performance was tested. Random permutations are evidence, not
  exhaustive proof of every possible future event graph.
- **Security observations:** Duplicate IDs with different content, sequence
  reuse, missing parents, cycles, malformed payloads, and unknown versions fail
  explicitly. Event authenticity remains a future design concern.
- **Privacy observations:** IDs, timestamps, sessions, playlists, tracks, and
  playback data are entirely synthetic.

## Production suitability and disposition

- **Production suitability:** The semantics are useful comparison input but
  this module is not a production sync implementation or canonical event schema.
- **Disposition:** **retain for comparison**.
- **Required target-device follow-up:** None for the pure reference-model exit
  criteria. Later candidate stacks should run the same corpus, and Phase 5 must
  prove real journal/transport/compaction behavior and resolve the documented
  policy gaps.
- **ADR implications:** The stack-selection and later sync ADRs must preserve
  idempotency, device sequence diagnostics, causal ordering, additive stats,
  and explicit conflicts. They must separately decide causal context and safe
  compaction. No ADR is created here.

Issue #9 is fully evidenced as a Phase 1 reference-model spike but remains open
while the draft evidence is reviewed and unmerged.
