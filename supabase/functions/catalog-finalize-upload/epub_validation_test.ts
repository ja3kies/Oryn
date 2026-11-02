import { strToU8, zipSync, type Zippable } from "npm:fflate@0.8.2";
import { EpubValidationError, validateEpub } from "./validation.ts";

function fixture(): Zippable {
  return {
    "mimetype": strToU8("application/epub+zip"),
    "META-INF/container.xml": strToU8('<container><rootfiles><rootfile full-path="OPS/book.opf" media-type="application/oebps-package+xml"/></rootfiles></container>'),
    "OPS/book.opf": strToU8('<package><metadata><title>Test</title><creator>Test</creator></metadata><manifest><item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="chapter"/></spine></package>'),
    "OPS/chapter.xhtml": strToU8('<html><body>unique-test-content</body></html>'),
  };
}
const blob = (entries: Zippable) => new Blob([new Uint8Array(zipSync(entries, { level: 0 })).buffer]);
async function rejects(file: Blob, reason: string | readonly string[]) {
  try { await validateEpub(file); }
  catch (error) {
    if (error instanceof EpubValidationError && (typeof reason === "string" ? error.message === reason : reason.includes(error.message))) return;
    throw error;
  }
  throw new Error("Hostile EPUB accepted");
}
Deno.test("real ZIP valid EPUB yields its SHA-256", async () => {
  if (!/^[a-f0-9]{64}$/.test(await validateEpub(blob(fixture())))) throw new Error("Missing integrity hash");
});
Deno.test("entry count cap rejects large directories before inspecting file contents", async () => {
  const entries = fixture();
  for (let i = 0; i < 5000; i++) entries[`extra-${i}.txt`] = new Uint8Array();
  await rejects(blob(entries), "archive_entry_count_out_of_range");
});
Deno.test("archive traversal and case collisions are rejected", async () => {
  await rejects(blob({ ...fixture(), "../outside.txt": strToU8("test") }), ["unsafe_archive_path", "invalid_or_corrupt_epub_archive"]);
  await rejects(blob({ ...fixture(), "ops/CHAPTER.xhtml": strToU8("test") }), "duplicate_archive_path");
});
Deno.test("DOCTYPE and XML entities are refused before parsing", async () => {
  const entries = fixture();
  entries["META-INF/container.xml"] = strToU8('<!DOCTYPE container [<!ENTITY test SYSTEM "file:///private">]><container>&test;</container>');
  await rejects(blob(entries), "xml_doctype_not_allowed");
});
Deno.test("missing manifest resource never produces a validated hash", async () => {
  const entries = fixture(); delete entries["OPS/chapter.xhtml"];
  await rejects(blob(entries), "opf_manifest_resource_missing");
});
Deno.test("high compression ratio is rejected before decompression", async () => {
  await rejects(blob({ ...fixture(), "bomb.txt": [strToU8("x".repeat(1024 * 1024)), { level: 9 }] }), "archive_compression_ratio_exceeded");
});
Deno.test("oversized XML cannot bypass bounded extraction", async () => {
  const entries = fixture(); entries["META-INF/container.xml"] = strToU8("x".repeat(1024 * 1024 + 1));
  await rejects(blob(entries), "expanded_entry_too_large");
});
Deno.test("corrupted stored content fails CRC validation", async () => {
  const bytes = new Uint8Array(await blob(fixture()).arrayBuffer());
  const needle = strToU8("unique-test-content");
  const index = bytes.findIndex((_, i) => needle.every((byte, j) => bytes[i + j] === byte));
  if (index < 0) throw new Error("Test fixture marker missing");
  bytes[index] ^= 1;
  await rejects(new Blob([bytes.buffer]), "invalid_or_corrupt_epub_archive");
});
