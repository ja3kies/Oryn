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
- Historical head 16c01a3c was not verified by that initial audit. The subsequent baseline 36d041b passed Android CI run 41768125989, checked through the API during finalization; current validation is recorded in ORYN_150_INSTALLATION.md.

## Residual adversarial risks
- The initial full-document scan risk was addressed with bounded, cancellable Unicode KMP in finalization. Android latency still requires device measurement; match materialization remains capped.
- Search dialog remains open during jumps. Validate focus, IME, repeated jump requests and actual reading position on a physical device at the next manual test milestone.
- EPUB pagination may be recomputed after font, viewport or theme changes. Verify that offsets and highlights remain aligned after repagination.
- Search matches use Kotlin ignore-case matching; validate Unicode case-folding expectations and document offset invariants.
- Existing release/community network and authentication restrictions must be regression-tested separately before enabling any remote features.

## Next gate
Review latest GitHub Actions test/assemble/signing checks. Address failures before declaring the search feature operational. Preserve stable branch and draft PR.

## Intensive finalization — 2026-11-04

Source and independent adversarial review covered the development branch, existing Auth/Edge locks, imports, private-file lifetimes, reader resources and CI. Consolidated functionality matrix, actual test/build results, artifact/signature instructions, residual device checklist and rollback are maintained in [ORYN_150_INSTALLATION.md](ORYN_150_INSTALLATION.md).

Confirmed findings below are local prioritization, not a claim of CVSS scoring or remote exploitation without user interaction:

| Priority | Demonstrated condition | Remediation and regression evidence |
| --- | --- | --- |
| P1 — integrity | Concurrent cover reference publication versus check/delete; stale entity updates could overwrite progress/favorites | Process-wide mutation coordination, targeted SQL updates, concurrent-publication/cancellation JVM regressions and real Room scenarios prepared |
| P1 — availability | ZipInputStream implicitly inflates ignored entries outside selected-content counters | Global bounded archive reader; real streaming ZIP tests exercise nextEntry/closeEntry/skip and aggregate budgets. Old behavior independently reproduced: 1,319 compressed bytes caused 1,048,576 inflated bytes without selected reads |
| P2 — confidentiality | Browser Cookie/Authorization forwarded to another HTTPS origin on a redirect | Credentials scoped by initial scheme/host/effective port; four policy regressions; no live credential sent |
| P2 — confidentiality | Debug println included selected book text | Removed; production-source log scan found no remaining println/Log calls |
| P2 — integrity | Same-URI, same-page-count replacement reused old PDF text cache | Cache identity includes SHA-256 of the exact bounded PDFBox snapshot; missing identity skips disk reuse; changed-byte/same-length regression |
| P2 — reliability | PDF reopening truncates an end-of-page float and can move back a page | Shared rounded restoration helper; all pages for document counts 1–500 round-trip in regression |
| P2 — resources | Canceled/replaced PDF loads and constructor/render failures retain native resources; oversized geometry inflates allocation | Explicit ownership/cleanup, off-UI close, bitmap dimensions/pixels/cache budgets and cancellation tests |

The input signature check is a bounded early rejection, **not a complete PDF structural validator**. Native loading still handles malformed PDF errors; native renderer/PDFBox robustness and exact resource consumption require actual-device fixtures. No arbitrary-path extraction existed: path rejection is defense in depth, not evidence of a demonstrated prior filesystem write exploit.

No new Auth/PKCE vulnerability was demonstrated; current state/expiry, HTTPS, Keystore and network locks were inspected without a production call. Real App Links, hardware-backed storage, isolated Auth and SQL/RLS were not executed without their prerequisites. No production, RLS, Storage, release gate, version or signing-key change was made.
