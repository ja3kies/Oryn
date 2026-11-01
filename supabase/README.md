# Supabase — Oryn Community Catalog

This directory contains versioned database changes for the opt-in EPUB catalog.

## Current state

- The initial schema migration is drafted on `feature/community-epub-catalog`.
- No Supabase project is connected to this workspace.
- The migration has **not** been applied or executed.
- The Android app is not connected to the catalogue.
- Do not enable community-catalog UI or claim upload/download is operational.

## Security boundary

- The `private` schema contains the only database `SECURITY DEFINER` helper.
- Keep `private` out of Supabase's exposed Data API schemas.
- The helper is limited to checking whether the authenticated caller owns the exact pending Storage object path.
- Creation, retraction, publication, EPUB validation, download authorization and object deletion belong in trusted Edge Functions.
- Each Edge Function must validate the bearer token with Supabase Auth (for example, `auth.getUser(accessToken)`) before using a server-only client.
- Never accept an owner ID as authority from the request body; derive it from the validated user.
- Never put a `service_role`/secret key in Android, source control, logs, or client-visible configuration.

## Database migration

The migration creates:

- `public.community_shared_books` with RLS enabled and forced.
- Authenticated read access to published rows and the caller's own rows, limited to selected metadata columns.
- No direct client INSERT/UPDATE/DELETE grants on the catalogue table.
- A private Storage bucket, limited to 50 MiB per object and EPUB/ZIP MIME declarations.
- A Storage INSERT policy that allows only the exact generated object path for the authenticated owner's pending record.
- No client Storage SELECT, UPDATE or DELETE policy for this bucket.

The MIME declaration and Storage size cap do **not** validate an EPUB archive. Publication must remain blocked until server-side validation succeeds.

## Required Edge Functions (not implemented)

1. `catalog-create-upload`: validate caller, validate metadata and consent, create a pending row, return its exact upload path.
2. `catalog-finalize-upload`: inspect ZIP/EPUB structure, enforce compressed and expanded size/entry limits, reject traversal/encrypted archives, calculate SHA-256, then queue moderation or publish under policy.
3. `catalog-retract`: validate owner, mark removed first, then delete the Storage object idempotently.
4. `catalog-download`: re-check published status and download policy on every request; return a short-lived signed URL or stream the object.
5. A moderation-only operation must be separately authorized and must not be callable by ordinary authenticated users.

Use short signed-URL lifetimes. Document that a URL already issued remains usable until expiry unless the underlying object is deleted or the relevant cache is invalidated. A downloaded local copy cannot be remotely recalled.

## Verification before Android integration

1. Apply only to a dedicated development Supabase project after review.
2. Keep `private` absent from the Data API exposed-schema list.
3. Run `supabase/tests/community_catalog_security.sql` against the migrated database.
4. Add runtime tests using two independent authenticated users and the anonymous role. Cover read, create, retract, upload, overwrite, download, publication and withdrawal.
5. Run Supabase Security Advisors and inspect every finding.
6. Complete EPUB validation, cleanup, quotas, abuse reporting and rights/takedown workflow.
7. Only then implement the Android repository and UI.

## Not yet implemented

- Trusted Edge Functions listed above.
- ZIP/EPUB validation, malware/content checks and SHA-256 processing.
- Moderation and publication lifecycle.
- Signed downloads, quota/rate limiting and idempotent physical deletion.
- Runtime cross-user/RLS/Storage integration tests.
- Abuse reporting, copyright takedown and retention policy.
- Android Auth/catalogue integration.
- Cloudflare Security Audit Skill execution.

This is a security-oriented schema baseline, **not a deployed or production-ready service**.
