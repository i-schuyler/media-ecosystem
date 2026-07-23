# Experimental identity-sidecar format

> This schema is a disposable issue #7 proof artifact. It is not the final
> production sidecar schema and creates no embedded-tag policy.

The canonicalized UTF-8 JSON envelope uses `schema_version: 1` and one of two
experimental kinds:

- `file_identity` requires an immutable `track_id`, distinct
  `file_instance_id`, and device-local `device_id`; it may carry `folder_id`,
  canonical `logical_path`, and an expected SHA-256 integrity value.
- `folder_identity` requires an immutable `folder_id` and may carry a canonical
  `logical_path`.

All identifiers use canonical lowercase UUID text. `extensions` is an object,
and unknown top-level fields are preserved during read-modify-write. Duplicate
JSON keys, malformed JSON, invalid identifiers, unsupported versions, unsafe
paths, and ambiguous inventory identities fail explicitly. A missing sidecar
returns an unidentified state; the harness never generates identity from name,
path, timestamp, size, tags, duration, or content hash.

Atomic writes use a same-directory temporary file, flush plus `fsync`,
`os.replace`, and directory `fsync` where the runtime supports opening a
directory. Targets are resolved beneath a registered disposable root, including
protection against a symlinked parent escaping that root. Windows directory
flush is not claimed by this candidate and remains a durability investigation.

Two intended copies can share a Track ID but must have distinct File Instance
IDs. Multiple sidecars with one Track ID in one device inventory are surfaced
for duplicate review. A modeled media replacement keeps Track ID only when the
caller explicitly authorizes replacement and supplies a new File Instance ID.

Embedded identity tags remain a later proof area. This sidecar experiment says
nothing about safe tag round trips for MP3, FLAC, AAC, Ogg Vorbis, ALAC, WAV, or
AIFF.
