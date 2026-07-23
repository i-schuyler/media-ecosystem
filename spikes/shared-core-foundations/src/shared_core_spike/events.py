"""Executable, non-production event-merge reference model for issue #9."""

from __future__ import annotations

from copy import deepcopy
from datetime import datetime
import hashlib
import json
from typing import Any, Iterable
import uuid


EVENT_SCHEMA_VERSION = 1
COMPACTION_SCHEMA_VERSION = 1
SUPPORTED_TYPES = {
    "playback_time_increment",
    "play_count",
    "rating_update",
    "resume_update",
    "playlist_addition",
    "destructive_intent",
}


class EventError(ValueError):
    """An event stream cannot be merged without guessing or data loss."""


def _canonical(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def _uuid(value: Any, field: str) -> None:
    if not isinstance(value, str):
        raise EventError(f"{field} must be a UUID string")
    try:
        parsed = uuid.UUID(value)
    except (AttributeError, ValueError) as error:
        raise EventError(f"{field} must be a valid UUID") from error
    if str(parsed) != value.lower():
        raise EventError(f"{field} must use canonical lowercase UUID text")


def validate(event: dict[str, Any]) -> dict[str, Any]:
    if not isinstance(event, dict):
        raise EventError("event must be an object")
    required = {"schema_version", "event_id", "device_id", "device_sequence", "display_timestamp", "type", "payload"}
    missing = sorted(required - event.keys())
    if missing:
        raise EventError("event is missing fields: " + ", ".join(missing))
    if type(event["schema_version"]) is not int or event["schema_version"] != EVENT_SCHEMA_VERSION:
        raise EventError("event schema version is unsupported")
    _uuid(event["event_id"], "event_id")
    _uuid(event["device_id"], "device_id")
    if type(event["device_sequence"]) is not int or event["device_sequence"] < 1:
        raise EventError("device_sequence must be a positive integer")
    if not isinstance(event["display_timestamp"], str):
        raise EventError("display_timestamp must be an ISO-8601 string")
    try:
        datetime.fromisoformat(event["display_timestamp"].replace("Z", "+00:00"))
    except ValueError as error:
        raise EventError("display_timestamp is not valid ISO-8601") from error
    if event["type"] not in SUPPORTED_TYPES:
        raise EventError("event type is unsupported")
    if not isinstance(event["payload"], dict):
        raise EventError("event payload must be an object")
    parents = event.get("causal_parents", [])
    if not isinstance(parents, list) or len(set(parents)) != len(parents):
        raise EventError("causal_parents must be a list without duplicates")
    for parent in parents:
        _uuid(parent, "causal parent")
        if parent == event["event_id"]:
            raise EventError("event cannot causally depend on itself")
    _validate_payload(event["type"], event["payload"])
    return event


def _validate_payload(event_type: str, payload: dict[str, Any]) -> None:
    track_types = {"playback_time_increment", "play_count", "rating_update", "resume_update", "playlist_addition"}
    if event_type in track_types:
        _uuid(payload.get("track_id"), "payload.track_id")
    if event_type == "playback_time_increment":
        if type(payload.get("seconds")) is not int or payload["seconds"] <= 0:
            raise EventError("playback increment seconds must be positive")
    elif event_type == "play_count":
        if not isinstance(payload.get("session_id"), str) or not payload["session_id"]:
            raise EventError("play-count event requires a session_id")
        if type(payload.get("qualified")) is not bool:
            raise EventError("play-count event qualified must be boolean")
    elif event_type == "rating_update":
        rating = payload.get("rating")
        if rating is not None and (type(rating) is not int or rating not in range(1, 6)):
            raise EventError("rating must be null or an integer from one through five")
    elif event_type == "resume_update":
        if type(payload.get("position_seconds")) is not int or payload["position_seconds"] < 0:
            raise EventError("resume position must be a non-negative integer")
        if type(payload.get("completed_meaningful_session")) is not bool:
            raise EventError("resume update must state whether its session completed meaningfully")
    elif event_type == "playlist_addition":
        for field in ("playlist_id", "entry_id"):
            _uuid(payload.get(field), f"payload.{field}")
    elif event_type == "destructive_intent":
        if not isinstance(payload.get("action"), str) or not isinstance(payload.get("target"), str):
            raise EventError("destructive intent requires action and target strings")


def _empty_model() -> dict[str, Any]:
    return {
        "compaction_schema_version": COMPACTION_SCHEMA_VERSION,
        "state": {
            "total_played_seconds": {},
            "play_count": {},
            "ratings": {},
            "rating_conflicts": [],
            "resume_positions": {},
            "resume_conflicts": [],
            "playlist_entries": [],
            "unresolved_destructive_intents": [],
        },
        "applied_event_ids": [],
        "event_hashes": {},
        "sequence_owners": {},
        "seen_sequences": {},
        "sequence_gaps": {},
        "counted_sessions": [],
        "event_index": {},
        "frontier": {"rating_update": {}, "resume_update": {}},
    }


def _load_base(base: dict[str, Any] | None) -> dict[str, Any]:
    if base is None:
        return _empty_model()
    if not isinstance(base, dict) or base.get("compaction_schema_version") != COMPACTION_SCHEMA_VERSION:
        raise EventError("compacted event snapshot has an unsupported version")
    required = set(_empty_model())
    if not required.issubset(base):
        raise EventError("compacted event snapshot is malformed")
    return deepcopy(base)


def merge(events: Iterable[dict[str, Any]], base: dict[str, Any] | None = None) -> dict[str, Any]:
    """Merge events idempotently; input order does not select conflict winners."""

    model = _load_base(base)
    applied = set(model["applied_event_ids"])
    imported: dict[str, dict[str, Any]] = {}
    for candidate in events:
        event = validate(candidate)
        event_id = event["event_id"]
        fingerprint = hashlib.sha256(_canonical(event).encode("utf-8")).hexdigest()
        if event_id in model["event_hashes"]:
            if model["event_hashes"][event_id] != fingerprint:
                raise EventError("an applied event_id was replayed with different content")
            continue
        if event_id in imported:
            if imported[event_id] != event:
                raise EventError("one event_id identifies different event documents")
            continue
        imported[event_id] = deepcopy(event)

    all_known_ids = applied | set(imported)
    for event in imported.values():
        for parent in event.get("causal_parents", []):
            if parent not in all_known_ids:
                raise EventError("causal parent is absent from both events and compacted history")

    _register_sequences(model, imported)
    _extend_event_index(model, imported)
    _reject_causal_cycles(model)

    for event_id in sorted(imported):
        event = imported[event_id]
        _apply_commutative(model, event)
        model["event_hashes"][event_id] = hashlib.sha256(_canonical(event).encode("utf-8")).hexdigest()
        applied.add(event_id)

    _resolve_causal_values(model, imported, "rating_update")
    _resolve_causal_values(model, imported, "resume_update")
    model["applied_event_ids"] = sorted(applied)
    model["counted_sessions"] = sorted(set(model["counted_sessions"]))
    model["state"]["playlist_entries"] = sorted(
        model["state"]["playlist_entries"], key=lambda item: (item["playlist_id"], item["event_id"])
    )
    model["state"]["unresolved_destructive_intents"] = sorted(
        model["state"]["unresolved_destructive_intents"], key=lambda item: item["event_id"]
    )
    _calculate_gaps(model)
    return model


def _register_sequences(model: dict[str, Any], imported: dict[str, dict[str, Any]]) -> None:
    for event_id, event in imported.items():
        device = event["device_id"]
        sequence = str(event["device_sequence"])
        owners = model["sequence_owners"].setdefault(device, {})
        if sequence in owners and owners[sequence] != event_id:
            raise EventError("device-local sequence was reused by a different event (impossible regression)")
        owners[sequence] = event_id
        seen = set(model["seen_sequences"].get(device, []))
        seen.add(event["device_sequence"])
        model["seen_sequences"][device] = sorted(seen)


def _calculate_gaps(model: dict[str, Any]) -> None:
    gaps: dict[str, list[int]] = {}
    for device, values in model["seen_sequences"].items():
        if not values:
            continue
        missing = sorted(set(range(1, max(values) + 1)) - set(values))
        if missing:
            gaps[device] = missing
    model["sequence_gaps"] = gaps


def _extend_event_index(model: dict[str, Any], imported: dict[str, dict[str, Any]]) -> None:
    for event_id, event in imported.items():
        model["event_index"][event_id] = {
            "device_id": event["device_id"],
            "device_sequence": event["device_sequence"],
            "causal_parents": sorted(event.get("causal_parents", [])),
        }


def _reject_causal_cycles(model: dict[str, Any]) -> None:
    index = model["event_index"]
    visiting: set[str] = set()
    visited: set[str] = set()

    def visit(event_id: str) -> None:
        if event_id in visiting:
            raise EventError("causal parent graph contains a cycle")
        if event_id in visited or event_id not in index:
            return
        visiting.add(event_id)
        for parent in index[event_id]["causal_parents"]:
            visit(parent)
        visiting.remove(event_id)
        visited.add(event_id)

    for event_id in index:
        visit(event_id)


def _apply_commutative(model: dict[str, Any], event: dict[str, Any]) -> None:
    state = model["state"]
    payload = event["payload"]
    event_type = event["type"]
    if event_type == "playback_time_increment":
        track = payload["track_id"]
        state["total_played_seconds"][track] = state["total_played_seconds"].get(track, 0) + payload["seconds"]
    elif event_type == "play_count" and payload["qualified"]:
        session_key = _canonical([event["device_id"], payload["track_id"], payload["session_id"]])
        if session_key not in model["counted_sessions"]:
            track = payload["track_id"]
            state["play_count"][track] = state["play_count"].get(track, 0) + 1
            model["counted_sessions"].append(session_key)
    elif event_type == "playlist_addition":
        existing = {item["entry_id"]: item for item in state["playlist_entries"]}
        entry = {
            "event_id": event["event_id"],
            "playlist_id": payload["playlist_id"],
            "entry_id": payload["entry_id"],
            "track_id": payload["track_id"],
        }
        if payload["entry_id"] in existing and existing[payload["entry_id"]] != entry:
            raise EventError("playlist entry_id collision cannot be merged safely")
        if payload["entry_id"] not in existing:
            state["playlist_entries"].append(entry)
    elif event_type == "destructive_intent":
        state["unresolved_destructive_intents"].append(
            {"event_id": event["event_id"], "action": payload["action"], "target": payload["target"], "status": "review_required"}
        )


def _happens_before(first: str, second: str, index: dict[str, Any]) -> bool:
    if first == second:
        return False
    first_meta = index[first]
    second_meta = index[second]
    if first_meta["device_id"] == second_meta["device_id"] and first_meta["device_sequence"] < second_meta["device_sequence"]:
        return True
    stack = list(second_meta["causal_parents"])
    visited: set[str] = set()
    while stack:
        current = stack.pop()
        if current == first:
            return True
        if current in visited or current not in index:
            continue
        visited.add(current)
        stack.extend(index[current]["causal_parents"])
    return False


def _resolve_causal_values(model: dict[str, Any], imported: dict[str, dict[str, Any]], event_type: str) -> None:
    frontier = model["frontier"][event_type]
    for event in imported.values():
        if event["type"] != event_type:
            continue
        if event_type == "resume_update" and not event["payload"]["completed_meaningful_session"]:
            continue
        track = event["payload"]["track_id"]
        frontier.setdefault(track, []).append(deepcopy(event))

    value_field = "rating" if event_type == "rating_update" else "position_seconds"
    value_target = "ratings" if event_type == "rating_update" else "resume_positions"
    conflict_target = "rating_conflicts" if event_type == "rating_update" else "resume_conflicts"
    state = model["state"]
    state[conflict_target] = []
    for track, candidates in sorted(frontier.items()):
        unique = {event["event_id"]: event for event in candidates}
        maximal = [
            event
            for event in unique.values()
            if not any(
                _happens_before(event["event_id"], other["event_id"], model["event_index"])
                for other in unique.values()
                if other["event_id"] != event["event_id"]
            )
        ]
        maximal.sort(key=lambda event: event["event_id"])
        frontier[track] = maximal
        if len(maximal) == 1:
            state[value_target][track] = maximal[0]["payload"][value_field]
        else:
            state[value_target].pop(track, None)
            state[conflict_target].append(
                {"track_id": track, "event_ids": [event["event_id"] for event in maximal], "status": "review_required"}
            )


def compact(model: dict[str, Any]) -> dict[str, Any]:
    """Return a deterministic JSON-compatible compacted model envelope."""

    loaded = _load_base(model)
    return json.loads(_canonical(loaded))


def public_state(model: dict[str, Any]) -> dict[str, Any]:
    return deepcopy(model["state"])
