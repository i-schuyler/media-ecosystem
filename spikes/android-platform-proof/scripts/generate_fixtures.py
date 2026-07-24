#!/usr/bin/env python3
"""Generate the tiny deterministic synthetic Android format-proof corpus."""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import struct
import subprocess
import tempfile
import wave
from pathlib import Path

SCHEMA_VERSION = "1.0.0"
SAMPLE_RATE = 48_000
CHANNELS = 2
SAMPLE_WIDTH_BITS = 16
DURATION_MS = 6_000
ARTIST = "Media Ecosystem Synthetic Lab"
ALBUM = "Disposable Phase 1 Format Proof"
FFMPEG_SHA256 = "464beb5e7bf0c311e68b45ae2f04e9cc2af88851abb4082231742a74d97b524c"
LAME_SHA256 = "3df5124d5ad3a98312ffd7ba6a9b36230e4f8a3e66d3ce0f425e336c32d216eb"

FIXTURES = (
    ("mp3-v0", "MP3 V0", "mp3-v0.mp3", "audio/mpeg", "MP3", "MPEG Layer III", "LAME -V 0"),
    ("mp3-320", "MP3 320", "mp3-320.mp3", "audio/mpeg", "MP3", "MPEG Layer III", "LAME -b 320"),
    ("flac", "FLAC", "flac.flac", "audio/flac", "FLAC", "FLAC", "FFmpeg compression_level 8"),
    ("aac", "AAC", "aac.m4a", "audio/mp4", "ISO BMFF/M4A", "AAC-LC", "FFmpeg 192 kbit/s AAC-LC"),
    ("ogg-vorbis", "Ogg Vorbis", "ogg-vorbis.ogg", "audio/ogg", "Ogg", "Vorbis", "FFmpeg quality 6"),
    ("alac", "ALAC", "alac.m4a", "audio/mp4", "ISO BMFF/M4A", "ALAC", "FFmpeg ALAC"),
    ("wav", "WAV", "wav.wav", "audio/wav", "RIFF/WAVE", "PCM signed 16-bit little-endian", "FFmpeg pcm_s16le"),
    ("aiff", "AIFF", "aiff.aiff", "audio/aiff", "AIFF", "PCM signed 16-bit big-endian", "FFmpeg pcm_s16be"),
)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(128 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def run(command: list[str]) -> None:
    subprocess.run(command, check=True)


def write_source_pcm(path: Path) -> None:
    """Write an integer-only triangle-tone sequence: 220, 330, then 440 Hz."""
    frequencies = (220, 330, 440)
    frames_per_tone = SAMPLE_RATE * 2
    amplitude = 1_200
    with wave.open(str(path), "wb") as output:
        output.setnchannels(CHANNELS)
        output.setsampwidth(SAMPLE_WIDTH_BITS // 8)
        output.setframerate(SAMPLE_RATE)
        frames = bytearray()
        for frequency in frequencies:
            period = SAMPLE_RATE // frequency
            for index in range(frames_per_tone):
                phase = index % period
                half = max(1, period // 2)
                if phase < half:
                    sample = -amplitude + (2 * amplitude * phase // half)
                else:
                    sample = amplitude - (2 * amplitude * (phase - half) // max(1, period - half))
                frames.extend(struct.pack("<hh", sample, -sample))
        output.writeframes(frames)


def metadata_args(title: str) -> list[str]:
    return [
        "-map_metadata", "-1",
        "-metadata", f"title={title}",
        "-metadata", f"artist={ARTIST}",
        "-metadata", f"album={ALBUM}",
        "-metadata", "date=2026",
    ]


def create_outputs(ffmpeg: str, lame: str, output_dir: Path) -> dict:
    output_dir.mkdir(parents=True, exist_ok=True)
    source = output_dir / ".synthetic-source.wav"
    write_source_pcm(source)
    commands: dict[str, list[str]] = {}

    mp3_commands = {
        "mp3-v0": [
            lame, "--silent", "-V", "0", "--vbr-new", "--add-id3v2",
            "--tt", "Synthetic MP3 V0", "--ta", ARTIST, "--tl", ALBUM,
            "--ty", "2026", str(source), str(output_dir / "mp3-v0.mp3"),
        ],
        "mp3-320": [
            lame, "--silent", "-b", "320", "--cbr", "--add-id3v2",
            "--tt", "Synthetic MP3 320", "--ta", ARTIST, "--tl", ALBUM,
            "--ty", "2026", str(source), str(output_dir / "mp3-320.mp3"),
        ],
    }
    for fixture_id, command in mp3_commands.items():
        run(command)
        commands[fixture_id] = command[1:-2] + ["<source-pcm>", f"<output>/{Path(command[-1]).name}"]

    ffmpeg_jobs = {
        "flac": (["-c:a", "flac", "-compression_level", "8", "-f", "flac"], "flac.flac"),
        "aac": (["-c:a", "aac", "-b:a", "192k", "-profile:a", "aac_low", "-f", "ipod"], "aac.m4a"),
        "ogg-vorbis": ([
            "-fflags", "+bitexact",
            "-c:a", "vorbis",
            "-q:a", "6",
            "-strict", "experimental",
            "-serial_offset", "424242",
            "-f", "ogg",
        ], "ogg-vorbis.ogg"),
        "alac": (["-c:a", "alac", "-f", "ipod"], "alac.m4a"),
        "wav": (["-c:a", "pcm_s16le", "-f", "wav"], "wav.wav"),
        "aiff": (["-c:a", "pcm_s16be", "-f", "aiff"], "aiff.aiff"),
    }
    title_by_id = {fixture_id: f"Synthetic {label}" for fixture_id, label, *_ in FIXTURES}
    for fixture_id, (codec_args, filename) in ffmpeg_jobs.items():
        command = [
            ffmpeg, "-hide_banner", "-loglevel", "error", "-nostdin", "-y",
            "-fflags", "+bitexact", "-i", str(source),
            *metadata_args(title_by_id[fixture_id]),
            "-flags:a", "+bitexact", *codec_args, str(output_dir / filename),
        ]
        run(command)
        commands[fixture_id] = [
            *command[1:command.index(str(source))], "<source-pcm>",
            *command[command.index(str(source)) + 1:-1], f"<output>/{filename}",
        ]

    source_hash = sha256(source)
    source.unlink()

    entries = []
    for fixture_id, label, filename, mime, container, codec, setting in FIXTURES:
        fixture_path = output_dir / filename
        entries.append(
            {
                "id": fixture_id,
                "required_format": label,
                "filename": filename,
                "mime_type": mime,
                "container": container,
                "codec": codec,
                "encoding_setting": setting,
                "expected_duration_ms": DURATION_MS,
                "duration_tolerance_ms": 350,
                "expected_metadata": {
                    "title": f"Synthetic {label}",
                    "artist": ARTIST,
                    "album": ALBUM,
                },
                "sha256": sha256(fixture_path),
                "size_bytes": fixture_path.stat().st_size,
                "generator_arguments": commands[fixture_id],
            }
        )

    manifest = {
        "schema_version": SCHEMA_VERSION,
        "provenance": "deterministic synthetic integer triangle-tone sequence; no human recording or personal media",
        "generator": {
            "script": "scripts/generate_fixtures.py",
            "python": "CPython 3.12.13",
            "ffmpeg": "8.1.2",
            "ffmpeg_source_sha256": FFMPEG_SHA256,
            "lame": "4.0",
            "lame_source_sha256": LAME_SHA256,
        },
        "source_pcm": {
            "sample_rate_hz": SAMPLE_RATE,
            "channels": CHANNELS,
            "sample_width_bits": SAMPLE_WIDTH_BITS,
            "duration_ms": DURATION_MS,
            "sequence_hz": [220, 330, 440],
            "amplitude_peak_pcm16": 1_200,
            "sha256": source_hash,
        },
        "fixtures": entries,
    }
    manifest_path = output_dir / "fixture-manifest.json"
    manifest_path.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    sums = [f"{entry['sha256']}  {entry['filename']}" for entry in entries]
    (output_dir / "SHA256SUMS").write_text("\n".join(sums) + "\n", encoding="utf-8")
    return manifest


def verify_reproducible(ffmpeg: str, lame: str, output_dir: Path) -> None:
    expected = json.loads((output_dir / "fixture-manifest.json").read_text(encoding="utf-8"))
    with tempfile.TemporaryDirectory(prefix="media-ecosystem-fixtures-") as temp:
        actual = create_outputs(ffmpeg, lame, Path(temp))
    expected_hashes = {item["filename"]: item["sha256"] for item in expected["fixtures"]}
    actual_hashes = {item["filename"]: item["sha256"] for item in actual["fixtures"]}
    if expected_hashes != actual_hashes:
        raise SystemExit(f"fixture reproduction mismatch: expected={expected_hashes} actual={actual_hashes}")
    print("Fixture generator reproduced all eight committed hashes.")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--ffmpeg", required=True)
    parser.add_argument("--lame", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--verify", action="store_true")
    args = parser.parse_args()
    if args.verify:
        verify_reproducible(args.ffmpeg, args.lame, args.output)
    else:
        create_outputs(args.ffmpeg, args.lame, args.output)


if __name__ == "__main__":
    main()
