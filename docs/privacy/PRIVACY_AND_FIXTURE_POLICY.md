# Privacy and Synthetic Fixture Policy

## Repository boundary

The public repository may contain:

- Source code
- Schemas
- Product and architecture documentation
- Synthetic devices, users, artists, albums, tracks, playlists, and events
- Tiny generated or clearly redistributable test media
- Redacted diagnostic examples

The public repository must never contain:

- Real audio from the user's library
- Real listening history
- Real Google Drive state
- OAuth credentials, refresh tokens, access tokens, or API secrets
- Personal absolute filesystem paths
- Personal email addresses used for synchronization
- Production databases or database snapshots
- Unredacted logs containing personal library information

## Test data

All committed test data must be obviously synthetic or have documented
redistribution permission.

Synthetic fixtures should use invented names and must not be derived by
anonymizing the user's real library.

## Destructive tests

Tests that rename, move, trash, restore, or delete files must operate only
inside disposable temporary roots created for the test.

No test or development command may perform a destructive operation outside
an explicitly registered Media Ecosystem test root.

## Pull-request requirement

Pull requests that affect identity, synchronization, migration, file
operations, trash, restoration, or duplicate cleanup must include tests for
the relevant invariants and failure behavior.
