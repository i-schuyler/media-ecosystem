# Security Policy

## Supported versions

Media Ecosystem is currently experimental pre-alpha software. No version is
yet supported for use with irreplaceable libraries.

## Reporting

Do not open a public issue containing credentials, personal library data,
private paths, OAuth material, or an exploit that could cause data loss.

Use GitHub private vulnerability reporting when enabled. Until then, contact
the repository owner privately through an established channel.

## High-severity areas

Treat issues in these areas as potentially high severity:

- Deletion outside a registered library root
- Silent identity mutation
- Credential exposure
- Unauthorized Drive access
- Path traversal
- Unsafe archive extraction
- Corrupt or non-idempotent synchronization
- Restore failures
- Bypassing stale-target checks
