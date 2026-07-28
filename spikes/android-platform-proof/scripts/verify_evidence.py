#!/usr/bin/env python3
"""Verify raw proof ZIPs and reproduce the sanitized physical-device report."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import zipfile
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
SCHEMA_DIR = ROOT / "app" / "src" / "main" / "assets" / "evidence"
ZIP_MEMBERS = {
    "evidence.json",
    "summary.md",
    "fixture-manifest.json",
    "fixture-SHA256SUMS",
    "build-metadata.json",
    "diagnostic.log",
    "CHECKSUMS.sha256",
}
ACTIVE_REQUIRED = {
    "mp3-v0": "MP3 V0",
    "mp3-320": "MP3 320",
    "flac": "FLAC",
    "aac": "AAC",
    "ogg-vorbis": "Ogg Vorbis",
    "wav": "WAV",
}
HISTORICAL_NONREQUIRED = {"alac": "ALAC", "aiff": "AIFF"}
DISPOSITIONS = {"passed", "failed", "inconclusive", "not run"}
PROHIBITED_FIELDS = {
    "raw_document_uri",
    "volume_id",
    "volume_uuid",
    "account_name",
    "wifi_ssid",
    "installed_applications",
    "serial_number",
    "advertising_id",
    "personal_path",
    "media_library",
}
PROHIBITED_TEXT = (
    re.compile(r"content://", re.IGNORECASE),
    re.compile(r"/storage/", re.IGNORECASE),
    re.compile(r"/home/", re.IGNORECASE),
    re.compile(r"[A-Za-z]:\\Users\\", re.IGNORECASE),
    re.compile(r"\b[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}\b"),
    re.compile(r"BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY", re.IGNORECASE),
    re.compile(r"\bgh[pousr]_[A-Za-z0-9_]{20,}\b"),
    re.compile(r"\bsk-[A-Za-z0-9_-]{20,}\b"),
)


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def validate_schema(instance: Any, schema: dict[str, Any], location: str = "$") -> None:
    """Validate the JSON-Schema subset used by the two proof schemas."""
    if "const" in schema and instance != schema["const"]:
        raise ValueError(f"{location}: expected constant {schema['const']!r}")
    if "enum" in schema and instance not in schema["enum"]:
        raise ValueError(f"{location}: value is outside declared enum")
    expected_type = schema.get("type")
    type_matches = {
        "object": isinstance(instance, dict),
        "array": isinstance(instance, list),
        "string": isinstance(instance, str),
        "integer": isinstance(instance, int) and not isinstance(instance, bool),
        "boolean": isinstance(instance, bool),
    }
    if expected_type and not type_matches.get(expected_type, False):
        raise ValueError(f"{location}: expected {expected_type}")
    if isinstance(instance, dict):
        required = set(schema.get("required", []))
        missing = required - set(instance)
        if missing:
            raise ValueError(f"{location}: missing fields {sorted(missing)}")
        properties = schema.get("properties", {})
        if schema.get("additionalProperties") is False:
            unexpected = set(instance) - set(properties)
            if unexpected:
                raise ValueError(f"{location}: unexpected fields {sorted(unexpected)}")
        for key, value in instance.items():
            if key in properties:
                validate_schema(value, properties[key], f"{location}.{key}")
    if isinstance(instance, list):
        if len(instance) < schema.get("minItems", 0):
            raise ValueError(f"{location}: too few items")
        if "maxItems" in schema and len(instance) > schema["maxItems"]:
            raise ValueError(f"{location}: too many items")
        if schema.get("uniqueItems") and len({json.dumps(x, sort_keys=True) for x in instance}) != len(instance):
            raise ValueError(f"{location}: duplicate items")
        if "items" in schema:
            for index, value in enumerate(instance):
                validate_schema(value, schema["items"], f"{location}[{index}]")
    if isinstance(instance, str) and "pattern" in schema:
        if re.fullmatch(schema["pattern"], instance) is None:
            raise ValueError(f"{location}: pattern mismatch")
    if isinstance(instance, int) and not isinstance(instance, bool):
        if instance < schema.get("minimum", instance):
            raise ValueError(f"{location}: below minimum")


def schema_for(version: str) -> dict[str, Any]:
    path_by_version = {
        "1.0.0": SCHEMA_DIR / "evidence-schema-v1.json",
        "1.1.0": SCHEMA_DIR / "evidence-schema-v1.1.json",
    }
    if version not in path_by_version:
        raise ValueError(f"Unsupported evidence schema: {version}")
    return json.loads(path_by_version[version].read_text(encoding="utf-8"))


def assert_no_prohibited_fields(value: Any, location: str = "$") -> None:
    if isinstance(value, dict):
        prohibited = set(value) & PROHIBITED_FIELDS
        if prohibited:
            raise ValueError(f"{location}: prohibited fields {sorted(prohibited)}")
        for key, child in value.items():
            assert_no_prohibited_fields(child, f"{location}.{key}")
    elif isinstance(value, list):
        for index, child in enumerate(value):
            assert_no_prohibited_fields(child, f"{location}[{index}]")


def classify_scope(fixture_id: str) -> str:
    if fixture_id in ACTIVE_REQUIRED:
        return "active_v1_required"
    if fixture_id in HISTORICAL_NONREQUIRED:
        return "nonrequired_historical_observation"
    raise ValueError(f"Unknown historical fixture ID: {fixture_id}")


def parse_checksum_manifest(value: bytes) -> dict[str, str]:
    result: dict[str, str] = {}
    for line in value.decode("utf-8").splitlines():
        digest, separator, name = line.partition("  ")
        if separator != "  " or not re.fullmatch(r"[0-9a-f]{64}", digest):
            raise ValueError("Malformed checksum manifest")
        if name in result:
            raise ValueError(f"Duplicate checksum entry: {name}")
        result[name] = digest
    return result


def read_verified_zip(
    archive_path: Path,
    expected_zip_sha256: str,
    expected_source_commit: str,
) -> tuple[dict[str, bytes], dict[str, Any], list[dict[str, Any]]]:
    archive_bytes = archive_path.read_bytes()
    actual_zip_sha256 = sha256_bytes(archive_bytes)
    if actual_zip_sha256 != expected_zip_sha256:
        raise ValueError(
            f"Whole-ZIP SHA-256 mismatch: expected={expected_zip_sha256} "
            f"actual={actual_zip_sha256}"
        )
    with zipfile.ZipFile(archive_path) as archive:
        infos = archive.infolist()
        names = [info.filename for info in infos]
        if len(names) != len(set(names)) or set(names) != ZIP_MEMBERS:
            raise ValueError(f"Evidence ZIP member allowlist mismatch: {names}")
        if any(info.is_dir() or info.file_size > 100_000 for info in infos):
            raise ValueError("Evidence ZIP contains a directory or oversized member")
        members = {name: archive.read(name) for name in names}

    internal = parse_checksum_manifest(members["CHECKSUMS.sha256"])
    if set(internal) != ZIP_MEMBERS - {"CHECKSUMS.sha256"}:
        raise ValueError("Internal checksum allowlist mismatch")
    for name, expected in internal.items():
        if sha256_bytes(members[name]) != expected:
            raise ValueError(f"Internal checksum mismatch: {name}")

    evidence = json.loads(members["evidence.json"])
    manifest = json.loads(members["fixture-manifest.json"])
    build = json.loads(members["build-metadata.json"])
    diagnostic = [
        json.loads(line)
        for line in members["diagnostic.log"].decode("utf-8").splitlines()
        if line
    ]
    validate_schema(evidence, schema_for(evidence["schema_version"]))
    if evidence["proof_app"]["source_commit"] != expected_source_commit:
        raise ValueError("Evidence source commit mismatch")
    if build["source_commit"] != expected_source_commit:
        raise ValueError("Build-metadata source commit mismatch")
    proof_app = evidence["proof_app"]
    dependency_keys = {
        "android_gradle_plugin": "android_gradle_plugin",
        "gradle": "gradle",
        "compile_sdk": "compile_sdk",
        "target_sdk": "target_sdk",
        "minimum_sdk": "minimum_sdk",
        "media3": "media3",
        "kotlin_application_source": "kotlin_application_source",
    }
    if build["version_name"] != proof_app["version"]:
        raise ValueError("Build/evidence application-version mismatch")
    if build["build_type"] != proof_app["build_variant"]:
        raise ValueError("Build/evidence variant mismatch")
    for build_key, evidence_key in dependency_keys.items():
        if build[build_key] != proof_app["dependencies"][evidence_key]:
            raise ValueError(f"Build/evidence dependency mismatch: {build_key}")
    if str(build["java_source_level"]) != proof_app["dependencies"]["java_toolchain"]:
        raise ValueError("Build/evidence Java toolchain mismatch")
    if build["media3"] != evidence["playback"]["candidate_version"]:
        raise ValueError("Build/playback candidate-version mismatch")
    if build["application_id"] != (
            "org.mediaecosystem.experimental.phase1platformproof.debug"
    ) or build["version_code"] < 1:
        raise ValueError("Unexpected disposable proof build identity")
    if sha256_bytes(members["fixture-manifest.json"]) != evidence["fixture_manifest_sha256"]:
        raise ValueError("Fixture-manifest hash mismatch")

    fixture_sums = parse_checksum_manifest(members["fixture-SHA256SUMS"])
    manifest_entries = manifest["fixtures"]
    if len(manifest_entries) not in {6, 8}:
        raise ValueError("Fixture manifest is neither active-six nor historical-eight")
    manifest_by_id = {entry["id"]: entry for entry in manifest_entries}
    if len(manifest_by_id) != len(manifest_entries):
        raise ValueError("Fixture manifest contains duplicate stable IDs")
    expected_ids = set(ACTIVE_REQUIRED)
    if len(manifest_entries) == 8:
        expected_ids |= set(HISTORICAL_NONREQUIRED)
    if set(manifest_by_id) != expected_ids:
        raise ValueError("Fixture stable-ID membership mismatch")
    if set(fixture_sums) != {entry["filename"] for entry in manifest_entries}:
        raise ValueError("Fixture checksum membership mismatch")
    for entry in manifest_entries:
        if fixture_sums[entry["filename"]] != entry["sha256"]:
            raise ValueError(f"Fixture hash manifest mismatch: {entry['id']}")
    matrix_by_id = {item["fixture_id"]: item for item in evidence["format_matrix"]}
    if set(matrix_by_id) != set(manifest_by_id) or len(matrix_by_id) != len(
            evidence["format_matrix"]
    ):
        raise ValueError("Evidence format-matrix stable-ID membership mismatch")
    for fixture_id, item in matrix_by_id.items():
        if item["fixture_sha256"] != manifest_by_id[fixture_id]["sha256"]:
            raise ValueError(f"Evidence fixture hash mismatch: {fixture_id}")

    assert_no_prohibited_fields(evidence)
    privacy_text = "\n".join(
        members[name].decode("utf-8", errors="replace") for name in sorted(members)
    )
    for pattern in PROHIBITED_TEXT:
        if pattern.search(privacy_text):
            raise ValueError(f"Privacy scan matched prohibited pattern: {pattern.pattern}")
    return members, evidence, diagnostic


def event_present(diagnostic: list[dict[str, Any]], event: str, status: str | None = None) -> bool:
    return any(
        item.get("event") == event
        and (status is None or item.get("status") == status)
        for item in diagnostic
    )


def physical_action(evidence: dict[str, Any], action: str) -> dict[str, Any]:
    matches = [item for item in evidence["physical_actions"] if item["action"] == action]
    if not matches:
        return {"acknowledged": False, "monotonic_duration_ms": 0}
    return {
        "acknowledged": bool(matches[-1]["acknowledged"]),
        "monotonic_duration_ms": int(matches[-1]["monotonic_duration_ms"]),
    }


def sanitized_report(
    expected_zip_sha256: str,
    evidence: dict[str, Any],
    diagnostic: list[dict[str, Any]],
) -> dict[str, Any]:
    formats = []
    for observed in evidence["format_matrix"]:
        fixture_id = observed["fixture_id"]
        disposition = observed["disposition"]
        if disposition not in DISPOSITIONS:
            raise ValueError(f"Invalid format disposition: {disposition}")
        scope = classify_scope(fixture_id)
        evaluation = disposition
        if fixture_id == "wav":
            evaluation = "targeted_retest_required"
        elif scope == "nonrequired_historical_observation":
            evaluation = "preserved_without_active_v1_requirement"
        formats.append({
            "fixture_id": fixture_id,
            "format": observed["required_format"],
            "scope": scope,
            "historical_harness_disposition": disposition,
            "active_contract_evaluation": evaluation,
            "open_result": observed.get("open_result"),
            "prepare_result": observed.get("prepare_result"),
            "playback_start_result": observed.get("playback_start_result"),
            "position_advancement": observed.get("position_advancement"),
            "seek_completion": observed.get("seek_completion"),
            "expected_duration_ms": observed.get("expected_duration_ms"),
            "duration_result_ms": observed.get("duration_result_ms"),
            "end_of_track_result": observed.get("end_of_track_result"),
            "basic_metadata_result": observed.get("basic_metadata_result"),
            "decoder": observed.get("decoder"),
            "warning_or_error": observed.get("warning_or_error"),
        })

    storage = evidence["storage"]
    screen_off = physical_action(evidence, "screen_off_playback")
    return {
        "report_schema_version": "1.0.0",
        "report_date": "2026-07-28",
        "source_evidence": {
            "whole_zip_sha256": expected_zip_sha256,
            "source_commit": evidence["proof_app"]["source_commit"],
            "evidence_schema_version": evidence["schema_version"],
            "schema_validation": "passed_against_corrected_v1_compatibility_schema",
            "schema_compatibility_note": (
                "The source-commit v1.0 schema omitted the export object emitted by "
                "the v1.0 app. The compatibility schema now describes that emitted "
                "object; the raw archive is unchanged."
            ),
            "exact_member_allowlist_verified": True,
            "internal_checksums_verified": True,
            "fixture_manifest_consistent": True,
            "build_metadata_verified": True,
            "privacy_scan_passed": True,
            "raw_archive_tracked": False,
        },
        "environment": evidence["environment"],
        "candidate": {
            "name": evidence["playback"]["candidate"],
            "version": evidence["playback"]["candidate_version"],
            "disposable_candidate_only": True,
            "production_engine_selected": False,
        },
        "active_v1_format_contract": {
            "amended_on": "2026-07-28",
            "required_count": 6,
            "required_format_ids": list(ACTIVE_REQUIRED),
            "required_formats": list(ACTIVE_REQUIRED.values()),
            "historical_nonrequired_format_ids": list(HISTORICAL_NONREQUIRED),
        },
        "storage": {
            "persisted_permission": storage["permission"],
            "marker_accessible_at_export": bool(storage["accessible"]),
            "marker_accessible_after_reboot": event_present(
                diagnostic, "remembered-root-probe", "passed"
            ) and any(
                item.get("event") == "remembered-root-probe"
                and item.get("details") == "REBOOTED_AND_STILL_ACCESSIBLE"
                for item in diagnostic
            ),
            "unavailable_not_deleted_assertion": bool(
                storage["unavailable_not_deleted_assertion"]
            ),
            "physical_unavailable_transition_observed": event_present(
                diagnostic, "remembered-root-probe", "unavailable"
            ),
            "intentional_process_termination_observed": event_present(
                diagnostic, "intentional-process-termination"
            ),
            "removal_reinsertion_performed": False,
            "permission_revocation_performed": event_present(
                diagnostic, "persisted-permission-revoked"
            ),
            "explicit_relink_performed": event_present(diagnostic, "explicit-relink"),
            "primary_device_constraint": (
                "The shared SIM/microSD tray is effectively permanent in ordinary "
                "use; removal would require case removal and a SIM-eject tool."
            ),
        },
        "playback": {
            "notification_play_pause": physical_action(
                evidence, "notification_play_pause"
            )["acknowledged"],
            "lock_screen_play_pause": physical_action(
                evidence, "lock_screen_play_pause"
            )["acknowledged"],
            "lock_screen_metadata": physical_action(
                evidence, "lock_screen_metadata"
            )["acknowledged"],
            "hardware_media_button": physical_action(
                evidence, "hardware_media_button"
            )["acknowledged"],
            "audio_focus_interruption": physical_action(
                evidence, "audio_focus_interruption"
            )["acknowledged"],
            "becoming_noisy": physical_action(
                evidence, "becoming_noisy"
            )["acknowledged"],
            "screen_off_required_duration_ms": 300_000,
            "screen_off_observed_duration_ms": screen_off["monotonic_duration_ms"],
            "screen_off_minimum_completed": screen_off["acknowledged"],
        },
        "format_observations": formats,
        "remaining_gaps": [
            "five-minute Android screen-off playback",
            "targeted corrected Android WAV end-of-track disposition",
            "all six required formats on Windows",
            "issue #2 removal/reinsertion or a documented future alternative",
        ],
        "privacy": {
            "raw_document_uri_included": False,
            "removable_volume_identifier_included": False,
            "account_or_wifi_data_included": False,
            "personal_filename_or_path_included": False,
            "serial_or_authentication_material_included": False,
        },
    }


def verify_sanitized_report(report: dict[str, Any]) -> None:
    contract = report["active_v1_format_contract"]
    if contract["required_count"] != 6:
        raise ValueError("Sanitized report does not declare exactly six active formats")
    if set(contract["required_format_ids"]) != set(ACTIVE_REQUIRED):
        raise ValueError("Sanitized report active format IDs mismatch")
    by_id = {item["fixture_id"]: item for item in report["format_observations"]}
    if set(by_id) != set(ACTIVE_REQUIRED) | set(HISTORICAL_NONREQUIRED):
        raise ValueError("Sanitized report did not preserve historical eight-format observations")
    for fixture_id in HISTORICAL_NONREQUIRED:
        if by_id[fixture_id]["scope"] != "nonrequired_historical_observation":
            raise ValueError(f"Historical scope missing for {fixture_id}")
    if by_id["wav"]["active_contract_evaluation"] != "targeted_retest_required":
        raise ValueError("WAV gap was not preserved")
    if not report["source_evidence"]["privacy_scan_passed"]:
        raise ValueError("Sanitized report privacy verification did not pass")
    assert_no_prohibited_fields(report)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("archive", nargs="?", type=Path)
    parser.add_argument("--expected-zip-sha256")
    parser.add_argument("--expected-source-commit")
    parser.add_argument("--write-sanitized-report", type=Path)
    parser.add_argument("--verify-sanitized-report", type=Path)
    args = parser.parse_args()

    if args.verify_sanitized_report:
        report = json.loads(args.verify_sanitized_report.read_text(encoding="utf-8"))
        verify_sanitized_report(report)
        print(
            "Verified sanitized report: six active formats, historical eight "
            "parseable, privacy boundary intact."
        )

    if args.archive:
        if not args.expected_zip_sha256 or not args.expected_source_commit:
            parser.error("archive verification requires both expected values")
        _, evidence, diagnostic = read_verified_zip(
            args.archive,
            args.expected_zip_sha256,
            args.expected_source_commit,
        )
        report = sanitized_report(args.expected_zip_sha256, evidence, diagnostic)
        verify_sanitized_report(report)
        if args.write_sanitized_report:
            args.write_sanitized_report.parent.mkdir(parents=True, exist_ok=True)
            args.write_sanitized_report.write_text(
                json.dumps(report, indent=2, sort_keys=True) + "\n",
                encoding="utf-8",
            )
        print(
            "Verified evidence ZIP allowlist, checksums, schema, manifest, build "
            "metadata, source commit, and privacy boundary."
        )
    elif not args.verify_sanitized_report:
        parser.error("provide an archive or --verify-sanitized-report")


if __name__ == "__main__":
    main()
