# Supabase — Oryn Community Catalog

This directory contains versioned database changes and Edge Functions for the opt-in EPUB catalogue.

## Current state

- The initial schema migration and four Edge Functions are committed on `feature/community-epub-catalog`.
- No Supabase project is connected to this workspace.
- No migration or Edge Function has been deployed or executed against a live project.
- Android is not connected to the catalogue.
- Do not enable community-catalog UI or claim upload/download is operational.

## Implemented: `catalog-create-upload`

Path: `functions/catalog-create-upload/`

- Requires POST/JSON and a valid bearer token; JWT verification is enabled in `config.toml`.
- Independently verifies the token with Auth `getUser(token)`; anonymous users are rejected.
- Accepts only `title`, `author`, `language` and `rightsConfirmed`.
- Requires explicit rights confirmation and stores a server-controlled declaration version.
- Bounds metadata and streams the request body with an 8 KiB cap.
- Derives the owner from the verified Auth user and creates a `pending` row.
- Returns the database-generated object path for a separate authenticated upload.
- The private bucket's object-size cap is 8 MiB. MIME declarations are not content validation.

The function does not upload a file, validate EPUB contents or publish a book. The file is downloadable only through `catalog-download` after publication. The service-role key is read only from server environment variables.

## Implemented: `catalog-finalize-upload`

Path: `functions/catalog-finalize-upload/`

- Accepts only a UUID `bookId`; verifies the bearer token independently and rejects anonymous users.
- Looks up the record using both the supplied ID and verified owner ID.
- Downloads only the generated Storage path from that owner-scoped row; the client cannot provide a path.
- Validates ZIP/EPUB structure with pinned `@zip.js/zip.js@2.24.0` and parses XML with `fast-xml-parser@5.11.2`.
- Requires the first uncompressed `mimetype` entry, exact EPUB MIME content, `META-INF/container.xml`, a safe OPF path, OPF metadata, manifest and spine.
- Rejects traversal/absolute paths, path collisions, encrypted entries, symlinks, ZIP64, unsupported compression, multi-disk archives and invalid CRC/local headers.
- Applies hard limits: 8 MiB compressed file, 250 entries, 8 MiB per entry, 32 MiB total expanded content, 1 MiB per required XML file and 100:1 maximum compression ratio.
- Streams each entry through a bounded sink and computes SHA-256 server-side.
- Stores the hash only. A validated book remains `pending`; publication requires a separate moderation-only operation.
- Repeated finalization is idempotent after the hash is stored.

A successful structural validation is **not** a malware scan, rights review or guarantee that every EPUB reader will render the book identically.

### Runtime capacity warning

Supabase currently documents a 256 MiB memory ceiling and 2 seconds of CPU time per Edge Function request. The conservative archive limits above reduce exposure but do not prove that full validation will fit the CPU budget for every valid EPUB. Benchmark on a dedicated development project with representative worst-case files before deployment. If the CPU ceiling is exceeded, move decompression/validation to a dedicated worker rather than raising the limits or weakening checks. See [Edge Function limits](https://supabase.com/docs/guides/functions/limits).

## Implemented: `catalog-retract`

Path: `functions/catalog-retract/`

- Accepts only one UUID `bookId`; rejects extra fields such as `owner_id` and `storage_key`.
- Requires JWT verification and independently verifies the bearer token with Auth `getUser(token)`; anonymous users are rejected.
- Looks up the book using both its ID and the verified owner ID.
- Checks that the stored object key still matches the database-generated `owner_id/book_id.epub` format.
- Changes the row to `removed` and sets `removed_at` **before** deleting the private Storage object, preventing further catalogue visibility first.
- Retains the removed metadata row as a tombstone; it does not erase the user's account or unrelated data.
- If object deletion fails, returns HTTP 202 with `cleanupPending`; retrying the same request attempts deletion again.
- Repeated requests for an already removed book retry object cleanup.

The service-role key is used only server-side. A failed object deletion does not restore publication or visibility.

## Server secrets

Required server environment variables are `SUPABASE_URL`, `SUPABASE_ANON_KEY` and `SUPABASE_SERVICE_ROLE_KEY`. The service-role key must never appear in Android, source control, logs or client-visible configuration.

## Unit tests

- Metadata tests: `deno test supabase/functions/catalog-create-upload/validation_test.ts`
- EPUB validation tests: `deno test supabase/functions/catalog-finalize-upload/validation_test.ts`
- Retraction input tests: `deno test supabase/functions/catalog-retract/validation_test.ts`
- Moderation input tests: `deno test supabase/functions/catalog-moderate/validation_test.ts`

Tests are committed but have **not** been executed in this workspace.

## Security boundary and database

- Keep Postgres schema `private` out of exposed Data API schemas.
- Its only `SECURITY DEFINER` helper checks the exact pending Storage path and has a pinned empty search path.
- RLS is enabled and forced on `community_shared_books`; direct client writes are revoked.
- The publication-state constraint permits a retracted book to retain its original `published_at` timestamp.
- Storage is private. The upload policy permits only the exact generated path for the authenticated owner.
- Never trust an owner ID or Storage path supplied by the client.
- Creation, finalization, retraction, publication, downloads and object deletion remain server-only.

## Implemented: `catalog-download`

Path: `functions/catalog-download/`

- Accepts POST/JSON only, with one UUID `bookId` and a 2 KiB request-body cap.
- Requires a valid bearer token verified independently with Auth `getUser(token)`; anonymous users are rejected.
- Re-queries the row on every request and permits only `published` records with a non-null SHA-256, non-null `published_at` and null `removed_at`.
- Returns the same 404 for missing, pending, rejected and removed records.
- Validates the database-generated `owner_id/book_id.epub` path and SHA-256 format; no path or owner can be supplied by the caller.
- Uses the server-only service-role client to retrieve the private object from its database-generated path, then streams the EPUB through the authenticated Edge Function.
- Enforces the bucket's 8 MiB maximum again before returning the file.
- Returns `application/epub+zip` with attachment disposition and `private, no-store` cache headers; no Storage URL or bearer download token is exposed.

A download already in progress or a local copy already saved by a user cannot be remotely recalled. Retraction prevents new requests from being authorized before object cleanup starts.

## Upload quotas and abandoned-upload cleanup

Migration: `migrations/20261101000200_catalog_quotas_and_expiry.sql`.

- Per-owner database limits are enforced atomically under an advisory transaction lock: at most 5 catalogue creation attempts per rolling hour, 10 active books (`pending` + `published`) and 3 pending uploads.
- Limit violations return HTTP 429 from `catalog-create-upload`; the database remains the authority, so concurrent requests cannot bypass the quota by racing.
- New pending uploads expire after 24 hours. Successful EPUB validation clears the upload deadline so a validated book can wait for moderation.
- `catalog-cleanup` changes overdue pending records to `expired` before deleting the private Storage object. Expired rows are retained as tombstones. Failed object deletion remains queued for a later run.
- `catalog-cleanup` has `verify_jwt = false` because it is intended for a scheduler, and therefore requires the high-entropy `CATALOG_CLEANUP_SECRET` header. Configure a random secret of at least 32 characters in Edge Function secrets and send it as `x-catalog-cleanup-secret`. Never commit the secret.
- Schedule `catalog-cleanup` periodically (for example hourly) using Supabase Cron/HTTP with the secret stored in Vault. The schedule is **not configured by this repository change**.

These controls do not yet provide global/IP request throttling or download-abuse protection. Keep platform-level rate limits enabled and add download/report abuse controls before public launch.

## Remaining work (not implemented)

1. Global/IP rate limits, download-abuse protection, abuse reporting, copyright takedown and retention policy.
2. Runtime cross-user/RLS/Storage integration tests and Supabase Security Advisors.
3. Configure the moderator allowlist secret in a real Supabase project, then test moderator and non-moderator sessions.
4. Android Auth/catalogue integration only after the server workflow is verified.

This is a security-oriented implementation baseline, **not a deployed or production-ready service**.


## Implemented: `catalog-moderate`

Path: `functions/catalog-moderate/`

- Accepts POST/JSON only, with a 2 KiB request-body cap and exactly `bookId` plus `decision` (`publish` or `reject`).
- Requires a bearer token verified independently with Auth `getUser(token)`; anonymous users are rejected.
- Authorizes moderators only through the server-side `CATALOG_MODERATOR_USER_IDS` environment variable (comma-separated Auth user UUIDs). No client metadata, request field or self-assigned role grants moderation rights.
- Uses the service-role client only server-side.
- Changes only records that are still `pending`, have a non-null SHA-256, and have no `removed_at` timestamp.
- Uses one conditional database update, so a concurrent retraction or prior moderation cannot be overwritten.
- Publishing sets `published_at`; rejection leaves it null. Missing, unvalidated, already moderated and retracted records share the same 409 response.

Configure `CATALOG_MODERATOR_USER_IDS` as an Edge Function secret containing only trusted moderator Auth UUIDs. Never commit moderator IDs or service-role credentials. If the allowlist is absent or malformed, moderation fails closed with 503.

Unit tests are committed at `functions/catalog-moderate/validation_test.ts`; they have not been executed.
