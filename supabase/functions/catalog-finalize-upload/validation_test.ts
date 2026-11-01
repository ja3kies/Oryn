import {
  isSafeArchivePath,
  MAX_COMPRESSION_RATIO,
  MAX_ENTRIES,
  MAX_FILE_BYTES,
  MAX_TOTAL_EXPANDED_BYTES,
} from "./validation.ts";
import { assertEquals } from "jsr:@std/assert@1.0.14";

Deno.test("accepts ordinary EPUB relative paths", () => {
  assertEquals(isSafeArchivePath("META-INF/container.xml"), true);
  assertEquals(isSafeArchivePath("OEBPS/Text/chapter-1.xhtml"), true);
});

Deno.test("rejects traversal, absolute and platform-specific paths", () => {
  for (const path of ["../secret", "OEBPS/../../secret", "/absolute", "\\server\\file", "C:/file", "a\\b", "a/./b", "a//b"]) {
    assertEquals(isSafeArchivePath(path), false, path);
  }
});

Deno.test("keeps validation limits bounded for the Edge runtime", () => {
  assertEquals(MAX_FILE_BYTES, 8 * 1024 * 1024);
  assertEquals(MAX_ENTRIES, 250);
  assertEquals(MAX_TOTAL_EXPANDED_BYTES, 32 * 1024 * 1024);
  assertEquals(MAX_COMPRESSION_RATIO, 100);
});
