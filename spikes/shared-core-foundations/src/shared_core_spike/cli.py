"""Command-line entry point for the disposable shared-core proof harness."""

from __future__ import annotations

import argparse
import copy
import json
import os
from pathlib import Path
import random
import sys
import unittest

from . import events, hashbench, paths


SPIKE_ROOT = Path(__file__).resolve().parents[2]


def _load_fixture(name: str):
    return json.loads((SPIKE_ROOT / "fixtures" / name).read_text(encoding="utf-8"))


def run_path_vectors() -> int:
    corpus = _load_fixture("path-vectors.json")
    passed = 0
    for vector in corpus["vectors"]:
        try:
            result = paths.canonicalize(vector["input"])
            if not vector["valid"]:
                raise AssertionError(f"{vector['name']}: unsafe vector was accepted")
            if result.display != vector["canonical"] or result.comparison_key != vector["comparison_key"]:
                raise AssertionError(f"{vector['name']}: canonical result differs")
        except paths.PathContractError as error:
            if vector["valid"] or vector["error"] not in str(error):
                raise AssertionError(f"{vector['name']}: unexpected rejection: {error}") from error
        passed += 1
    for vector in corpus["collision_sets"]:
        collisions = paths.detect_case_collisions(vector["paths"])
        if vector["collision_key"] not in collisions:
            raise AssertionError(f"{vector['name']}: collision was not detected")
        passed += 1
    print(json.dumps({"command": "paths", "contract_version": corpus["contract_version"], "vectors_passed": passed}, sort_keys=True))
    return 0


def run_event_permutations(seed: int, iterations: int) -> int:
    documents = _load_fixture("event-corpus.json")["events"]
    expected = events.public_state(events.merge(documents))
    for iteration in range(iterations):
        shuffled = copy.deepcopy(documents)
        random.Random(seed * 1000 + iteration).shuffle(shuffled)
        if events.public_state(events.merge(shuffled)) != expected:
            raise AssertionError(f"event convergence failed for seed {seed}, iteration {iteration}")
    print(json.dumps({"command": "events", "seed": seed, "iterations": iterations, "result": "passed"}, sort_keys=True))
    return 0


def run_verify(seeds: str) -> int:
    os.environ["SPIKE_EVENT_SEEDS"] = seeds
    suite = unittest.defaultTestLoader.discover(str(SPIKE_ROOT / "tests"), pattern="test_*.py")
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    if not result.wasSuccessful():
        return 1
    smoke = hashbench.correctness_probe()
    print(json.dumps({"command": "verify", "event_seeds": [int(value) for value in seeds.split(",")], "hash_smoke": smoke, "result": "passed"}, sort_keys=True))
    return 0


def _sizes(value: str) -> list[int]:
    try:
        values = [int(item) * 1024 * 1024 for item in value.split(",")]
    except ValueError as error:
        raise argparse.ArgumentTypeError("sizes must be comma-separated integer MiB values") from error
    if not values or any(item <= 0 for item in values):
        raise argparse.ArgumentTypeError("sizes must be positive")
    return values


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Disposable Phase 1 shared-core capability-proof harness (not production code)."
    )
    subparsers = parser.add_subparsers(dest="command", required=True)
    verify = subparsers.add_parser("verify", help="run all proofs and a small hash smoke test")
    verify.add_argument("--seeds", default="7,20260723", help="recorded comma-separated event permutation seeds")
    subparsers.add_parser("paths", help="run the portable path vector corpus")
    event_parser = subparsers.add_parser("events", help="run seeded event delivery permutations")
    event_parser.add_argument("--seed", type=int, required=True)
    event_parser.add_argument("--iterations", type=int, default=100)
    subparsers.add_parser("hash-smoke", help="verify repeated digest and one-byte mutation behavior")
    benchmark_parser = subparsers.add_parser("hash-benchmark", help="run an explicit synthetic full-file benchmark")
    benchmark_parser.add_argument("--sizes-mib", type=_sizes, default=_sizes("1,16,64,256"))
    benchmark_parser.add_argument("--repeats", type=int, default=3)
    benchmark_parser.add_argument("--chunk-mib", type=int, default=1)
    benchmark_parser.add_argument("--device-label", required=True)
    benchmark_parser.add_argument("--json-output", type=Path, required=True)
    benchmark_parser.add_argument("--markdown-output", type=Path, required=True)
    args = parser.parse_args(argv)

    if args.command == "verify":
        return run_verify(args.seeds)
    if args.command == "paths":
        return run_path_vectors()
    if args.command == "events":
        return run_event_permutations(args.seed, args.iterations)
    if args.command == "hash-smoke":
        print(json.dumps(hashbench.correctness_probe(), sort_keys=True))
        return 0
    if args.command == "hash-benchmark":
        result = hashbench.benchmark(
            sizes_bytes=args.sizes_mib,
            repeats=args.repeats,
            chunk_size=args.chunk_mib * 1024 * 1024,
            device_label=args.device_label,
        )
        hashbench.write_results(result, args.json_output, args.markdown_output)
        print(json.dumps({"measurements": len(result["measurements"]), "json": str(args.json_output), "markdown": str(args.markdown_output)}, sort_keys=True))
        return 0
    parser.error("unsupported command")
    return 2


if __name__ == "__main__":
    sys.exit(main())
