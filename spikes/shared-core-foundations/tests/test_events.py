from __future__ import annotations

import copy
import os
import random
import unittest

from shared_core_spike import events
from support import DEVICE_A, TRACK_ID, load_json


def corpus():
    return load_json("event-corpus.json")["events"]


class EventMergeTests(unittest.TestCase):
    def test_additive_stats_causal_values_and_explicit_conflicts(self):
        documents = corpus()
        self.assertEqual(len(documents), len({event["event_id"] for event in documents}))
        self.assertEqual(2, len({event["device_id"] for event in documents}))
        result = events.merge(documents)
        state = events.public_state(result)
        self.assertEqual(240, state["total_played_seconds"][TRACK_ID])
        self.assertEqual(2, state["play_count"][TRACK_ID])
        self.assertEqual(4, state["ratings"][TRACK_ID])
        self.assertEqual(250, state["resume_positions"][TRACK_ID])
        self.assertEqual(2, len(state["playlist_entries"]))
        self.assertEqual("review_required", state["unresolved_destructive_intents"][0]["status"])

    def test_reimport_never_double_counts(self):
        once = events.merge(corpus())
        twice = events.merge(corpus(), base=events.compact(once))
        self.assertEqual(events.public_state(once), events.public_state(twice))
        self.assertEqual(once["applied_event_ids"], twice["applied_event_ids"])

    def test_permutations_converge_with_recorded_seeds(self):
        baseline = events.public_state(events.merge(corpus()))
        seeds = [int(value) for value in os.environ.get("SPIKE_EVENT_SEEDS", "7,20260723").split(",")]
        for seed in seeds:
            for iteration in range(20):
                with self.subTest(seed=seed, iteration=iteration):
                    shuffled = copy.deepcopy(corpus())
                    random.Random(seed * 1000 + iteration).shuffle(shuffled)
                    self.assertEqual(baseline, events.public_state(events.merge(shuffled)))

    def test_wall_clock_does_not_override_causality(self):
        result = events.merge(corpus())
        self.assertEqual(4, result["state"]["ratings"][TRACK_ID])
        winning = result["frontier"]["rating_update"][TRACK_ID][0]
        self.assertEqual("2000-01-01T00:00:00Z", winning["display_timestamp"])

    def test_sequences_detect_gaps_and_impossible_reuse(self):
        missing_sequence = [event for event in corpus() if event["event_id"] != "00000000-0000-4000-8000-000000000002"]
        gap_result = events.merge(missing_sequence)
        self.assertEqual([2], gap_result["sequence_gaps"][DEVICE_A])
        collision = copy.deepcopy(corpus()[0])
        collision["event_id"] = "99999999-9999-4999-8999-999999999999"
        with self.assertRaisesRegex(events.EventError, "impossible regression"):
            events.merge([corpus()[0], collision])

    def test_compaction_plus_remaining_events_matches_one_pass_and_replay_is_ignored(self):
        documents = corpus()
        compacted = events.compact(events.merge(documents[:8]))
        continued = events.merge([documents[0], *documents[8:]], base=compacted)
        one_pass = events.merge(documents)
        self.assertEqual(events.public_state(one_pass), events.public_state(continued))

    def test_duplicate_session_does_not_double_play_count(self):
        documents = corpus()
        duplicate_session = copy.deepcopy(documents[4])
        duplicate_session["event_id"] = "99999999-9999-4999-8999-999999999998"
        duplicate_session["device_sequence"] = 9
        result = events.merge([*documents, duplicate_session])
        self.assertEqual(2, result["state"]["play_count"][TRACK_ID])

    def test_concurrent_rating_and_resume_updates_remain_unresolved(self):
        result = events.merge(corpus()[:8])
        state = result["state"]
        self.assertNotIn(TRACK_ID, state["ratings"])
        self.assertEqual("review_required", state["rating_conflicts"][0]["status"])
        resume_only = events.merge(corpus()[9:11])
        self.assertNotIn(TRACK_ID, resume_only["state"]["resume_positions"])
        self.assertEqual("review_required", resume_only["state"]["resume_conflicts"][0]["status"])

    def test_malformed_corrupt_and_unknown_versions_fail_safely(self):
        malformed = copy.deepcopy(corpus()[0])
        malformed.pop("payload")
        with self.assertRaisesRegex(events.EventError, "missing fields"):
            events.merge([malformed])
        unknown = copy.deepcopy(corpus()[0])
        unknown["schema_version"] = 99
        with self.assertRaisesRegex(events.EventError, "unsupported"):
            events.merge([unknown])
        corrupt = copy.deepcopy(corpus()[0])
        corrupt["payload"]["seconds"] = -1
        with self.assertRaisesRegex(events.EventError, "positive"):
            events.merge([corrupt])
        with self.assertRaisesRegex(events.EventError, "unsupported version"):
            events.merge([], base={"compaction_schema_version": 99})

    def test_missing_parents_cycles_and_event_id_collisions_fail_safely(self):
        missing_parent = copy.deepcopy(corpus()[0])
        missing_parent["causal_parents"] = ["88888888-8888-4888-8888-888888888888"]
        with self.assertRaisesRegex(events.EventError, "causal parent is absent"):
            events.merge([missing_parent])

        first, second = copy.deepcopy(corpus()[:2])
        first["causal_parents"] = [second["event_id"]]
        second["causal_parents"] = [first["event_id"]]
        with self.assertRaisesRegex(events.EventError, "cycle"):
            events.merge([first, second])

        collision = copy.deepcopy(corpus()[0])
        collision["payload"]["seconds"] = 61
        with self.assertRaisesRegex(events.EventError, "different event documents"):
            events.merge([corpus()[0], collision])


if __name__ == "__main__":
    unittest.main()
