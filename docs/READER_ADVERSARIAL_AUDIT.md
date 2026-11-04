# Reader adversarial audit — 2026-11-04

Scope: reader controls, chapter progress, in-book search, table of contents; static source review and GitHub Actions evidence. No physical Android device test.

## Confirmed defects and remediation
- Duplicate TOC offsets could yield a zero-length chapter interval and no chapter progress. Normalize duplicate offsets in chapter-progress calculation. Regression test added.
- Corrupt, unordered or repeated page boundaries could yield misleading remaining-page counts. Reject malformed snapshots. Regression tests added.
- TOC scroll position was initialized only once even if the reading position changed while the dialog remained composed. Re-key scroll effect on entries and active chapter; expose selected semantics.
- In-book search previously had no previous/next controls. Add bounded search matches, current-position starting index, wraparound navigation and regression tests.
- Search results were keyed only on the query; replacing a document with identical query could show stale results. Key computation and selected index on document text.

## CI evidence and limitations
- Commit 7e873936 passed Android CI (run 41755298923).
- Commit c9036ae7 failed compilation (run 41756201752): ReaderScreen did not yet pass the new currentOffset parameter. Commit f719ee48 subsequently added the call-site argument; CI for later commits must be checked independently.
- Current head 16c01a3c is not yet verified. No claim of passing tests, APK installation or UI behavior until evidence exists.

## Residual adversarial risks
- Full-document search scans the entire text per query after a debounce. Stress-test multi-megabyte EPUBs for memory and latency; limit match materialization and retain cancellation.
- Search dialog remains open during jumps. Validate focus, IME, repeated jump requests and actual reading position on a physical device at the next manual test milestone.
- EPUB pagination may be recomputed after font, viewport or theme changes. Verify that offsets and highlights remain aligned after repagination.
- Search matches use Kotlin ignore-case matching; validate Unicode case-folding expectations and document offset invariants.
- Existing release/community network and authentication restrictions must be regression-tested separately before enabling any remote features.

## Next gate
Review latest GitHub Actions test/assemble/signing checks. Address failures before declaring the search feature operational. Preserve stable branch and draft PR.
