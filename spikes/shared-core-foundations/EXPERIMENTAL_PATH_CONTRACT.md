# Experimental logical-path contract

> This contract governs issue #6's disposable proof harness only. It is not a
> production storage schema or final cross-platform filesystem policy.

## Shared representation

A shared logical path is a non-empty root-relative sequence of display-name
components. It contains no Android storage root, Windows drive, UNC server,
mount identifier, or other physical location. Its canonical separator is `/`.
Backslashes are accepted only as input separators and normalized before
validation.

Every component is normalized to Unicode NFC. The canonical display path keeps
case. A separate collision key applies NFC and Unicode `casefold()` to each
component. Two display paths with the same key are reported as a collision;
the harness never chooses, renames, merges, or deletes either path.

## Fail-closed rules

The harness rejects:

- POSIX absolute, drive-letter, and UNC paths;
- empty, `.`, and `..` components;
- control characters and Windows-invalid `< > : " | ? *` characters;
- Windows-reserved base names, including when followed by an extension;
- component names ending in a dot or space;
- components over 255 UTF-8 bytes; and
- complete paths over 1,024 UTF-8 bytes.

The byte limits are an experimental shared-state policy, not a claim that all
target filesystems expose identical limits. Windows component behavior is often
described in UTF-16 code units, and filesystem/API long-path handling varies;
the target runs must test the exact primary devices before this policy can
inform production architecture.

## Device-local roots and operations

A registered physical root is held only in device-local state. Resolving a
logical path checks containment under that root. If the root is missing, the
harness reports `unavailable; no deletion is implied`. Relinking replaces only
the physical root mapping; the logical path is unchanged.

Rename changes one validated final component. Move combines the validated file
name with a separately validated logical parent. Both operations round-trip
through the same canonicalizer. Display names can intentionally differ from
comparison keys, for example `Beyoncé/LOUD Name.FLAC` and
`beyoncé/loud name.flac`.

## Falsifiable corpus

[`fixtures/path-vectors.json`](fixtures/path-vectors.json) records the contract
version, limits, positive results, expected rejections, and collision sets.
Run it unchanged on all environments:

```sh
python3 spikes/shared-core-foundations/scripts/harness.py paths
```

The VPS run proves deterministic CPython behavior and registered-root safety on
the recorded Linux filesystem. Android Termux and Windows 11 execution, actual
filesystem name creation, removable-storage lifecycle behavior, and Explorer or
Storage Access Framework behavior remain target-device work.

