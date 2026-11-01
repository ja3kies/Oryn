# Supabase — Oryn Community Catalog

This directory contains versioned database changes and Edge Functions for the opt-in EPUB catalogue.

## Current state

- The initial schema migration and two Edge Functions are committed on `feature/community-epub-catalog`.
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

The function does not upload a file, validate EPUB contents, publish a book or issue a download URL. The service-role key is read only from server environment variables.

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

## Server secrets

Required server environment variables are `SUPABASE_URL`, `SUPABASE_ANON_KEY` and `SUPABASE_SERVICE_ROLE_KEY`. The service-role key must never appear in Android, source control, logs or client-visible configuration.

## Unit tests

- Metadata tests: `deno test supabase/functions/catalog-create-upload/validation_test.ts`
- EPUB validation tests: `deno test supabase/functions/catalog-finalize-upload/validation_test.ts`

Tests are committed but have **not** been executed in this workspace.

## Security boundary and database

- Keep Postgres schema `private` out of exposed Data API schemas.
- Its only `SECURITY DEFINER` helper checks the exact pending Storage path and has a pinned empty search path.
- RLS is enabled and forced on `community_shared_books`; direct client writes are revoked.
- Storage is private. The upload policy permits only the exact generated path for the authenticated owner.
- Never trust an owner ID or Storage path supplied by the client.
- Creation, finalization, retraction, publication, downloads and object deletion remain server-only.

## Remaining work (not implemented)

1. `catalog-retract`: mark removed first, then delete the object idempotently.
2. `catalog-download`: re-check published status and policy on every request; use short-lived signed URLs or streaming.
3. Moderation-only publication operation with explicit administrator authorization.
4. Rate limits, quotas, abandoned-upload cleanup, abuse reporting, copyright takedown and retention policy.
5. Runtime cross-user/RLS/Storage integration tests and Supabase Security Advisors.
6. Android Auth/catalogue integration only after the server workflow is verified.

A signed URL remains usable until expiry unless the object is deleted or cache invalidation is effective. A downloaded local copy cannot be remotely recalled.

This is a security-oriented implementation baseline, **not a deployed or production-ready service**.
