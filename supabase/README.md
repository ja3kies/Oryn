# Supabase — Oryn Community Catalog

This directory contains versioned database changes and Edge Functions for the opt-in EPUB catalogue.

## Current state

- The initial schema migration and first Edge Function are committed on `feature/community-epub-catalog`.
- No Supabase project is connected to this workspace.
- No migration or Edge Function has been deployed or executed against a live project.
- Android is not connected to the catalogue.
- Do not enable community-catalog UI or claim upload/download is operational.

## Implemented: `catalog-create-upload`

Path: `functions/catalog-create-upload/`

The function:

1. Requires POST with JSON and a valid bearer token (`verify_jwt = true`).
2. Independently verifies the token with Supabase Auth `getUser(token)`.
3. Rejects anonymous Auth users.
4. Accepts only `title`, `author`, `language` and `rightsConfirmed`; rejects unknown fields such as `owner_id` and `storage_key`.
5. Requires explicit `rightsConfirmed: true` and stores a server-controlled declaration version.
6. Bounds metadata and streams the request body with an 8 KiB cap.
7. Derives `owner_id` only from the verified Auth user.
8. Creates a `pending` row and returns its generated object path and the 50 MiB upload limit.

It does **not** upload a file, validate EPUB contents, publish a book, or issue a download URL. The Android client must upload the selected file separately using the user's own authenticated session and the exact returned path. No file is uploaded merely by calling this function.

The service-role key is read only from the Edge Function environment and is never returned to clients. Required server environment variables are `SUPABASE_URL`, `SUPABASE_ANON_KEY`, and `SUPABASE_SERVICE_ROLE_KEY`.

Unit tests for metadata validation are in `validation_test.ts`. They require Deno and can be run from the repository root with:

```sh
deno test supabase/functions/catalog-create-upload/validation_test.ts
```

## Security boundary

- Keep the Postgres `private` schema out of the exposed Data API schemas.
- The `private` schema contains the only database `SECURITY DEFINER` helper; it only checks ownership of the exact pending Storage path.
- Creation, retraction, publication, EPUB validation, download authorization and object deletion are server-only operations.
- Each Edge Function must validate the bearer token with Supabase Auth before using a server-only client.
- Never accept an owner ID as authority from the request body.
- Never put a `service_role`/secret key in Android, source control, logs, or client-visible configuration.

## Database migration

The migration creates:

- `public.community_shared_books` with RLS enabled and forced.
- Authenticated read access to published rows and the caller's own rows, limited to selected metadata columns.
- No direct client INSERT/UPDATE/DELETE grants on the catalogue table.
- A private Storage bucket, limited to 50 MiB per object and EPUB/ZIP MIME declarations.
- A Storage INSERT policy that allows only the exact generated object path for the authenticated owner's pending record.
- No client Storage SELECT, UPDATE or DELETE policy for this bucket.

MIME declarations and Storage size caps do **not** validate an EPUB archive. Publication must remain blocked until server-side validation succeeds.

## Remaining Edge Functions (not implemented)

1. `catalog-finalize-upload`: inspect ZIP/EPUB structure, enforce compressed and expanded size/entry limits, reject traversal/encrypted archives, calculate SHA-256, then queue moderation or publish under policy.
2. `catalog-retract`: validate owner, mark removed first, then delete the Storage object idempotently.
3. `catalog-download`: re-check published status and download policy on every request; return a short-lived signed URL or stream the object.
4. A moderation-only operation must be separately authorized and must not be callable by ordinary authenticated users.

Use short signed-URL lifetimes. A URL already issued remains usable until expiry unless the object is deleted or cache invalidation is effective. A downloaded local copy cannot be remotely recalled.

## Verification before Android integration

1. Apply the migration only to a dedicated development Supabase project after review.
2. Keep `private` absent from the Data API exposed-schema list.
3. Run `supabase/tests/community_catalog_security.sql` against the migrated database.
4. Run the Deno unit tests and add Edge Function integration tests.
5. Test with two independent authenticated users and the anonymous role: read, create, retract, upload, overwrite, download, publication and withdrawal.
6. Run Supabase Security Advisors and inspect every finding.
7. Complete EPUB validation, cleanup, quotas, abuse reporting and rights/takedown workflow.
8. Only then implement the Android repository and UI.

## Not yet implemented

- EPUB ZIP/container/OPF validation, malware/content checks and SHA-256 processing.
- Moderation and publication lifecycle.
- Signed downloads, quota/rate limiting and idempotent physical deletion.
- Runtime cross-user/RLS/Storage integration tests.
- Abuse reporting, copyright takedown and retention policy.
- Android Auth/catalogue integration.
- Cloudflare Security Audit Skill execution.

This is a security-oriented implementation baseline, **not a deployed or production-ready service**.
