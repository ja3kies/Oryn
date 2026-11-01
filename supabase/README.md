# Supabase — Oryn Community Catalog

This directory contains versioned database changes for the opt-in EPUB catalog.

## Current state

- Initial schema and private Storage bucket migration is drafted.
- No Supabase project has been linked or migrated from this repository yet.
- The Android app is not connected to this schema.
- Do not enable community catalog UI or claim upload/download is operational.

## Apply

1. Create or select a dedicated Supabase project.
2. Review `migrations/20261101000100_community_catalog.sql`.
3. Apply it to a non-production development project first.
4. Run the authorization tests with at least two test users and the anonymous role.
5. Configure trusted Edge Functions for EPUB validation, publication, signed downloads, and object deletion.
6. Only then wire the Android client.

## Security properties in this first migration

- The bucket is private and limited to 50 MiB per object.
- Anonymous users receive no table or Storage access.
- Authenticated users can read only published records or their own records.
- Direct client INSERT/UPDATE/DELETE on the table is revoked.
- Creation records consent and always creates a `pending` row through a narrow RPC.
- Clients can upload only to the exact generated path of their own pending record.
- Clients cannot read or delete Storage objects directly.
- Retraction immediately changes the row to `removed`; trusted server-side cleanup of the physical object is a required follow-up.

## Not yet implemented

- EPUB ZIP/container/OPF validation and malware/content scanning.
- Server-side publication/moderation workflow and SHA-256 calculation.
- Signed download Edge Function with status and quota checks.
- Storage object cleanup after withdrawal/deletion.
- Rate limits, abuse reporting, copyright takedown workflow, and retention policy.
- pgTAP/integration tests proving cross-user isolation.

The first migration is a security baseline, not a complete or production-ready service.
