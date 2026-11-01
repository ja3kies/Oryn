import { assertEquals } from "jsr:@std/assert@1.0.14";
import {
  isExpectedStorageKey,
  parseRetractionInput,
  RetractionInputError,
} from "./validation.ts";

const ownerId = "123e4567-e89b-42d3-a456-426614174000";
const bookId = "987e6543-e21b-42d3-a456-426614174000";

Deno.test("accepts exactly one valid bookId", () => {
  assertEquals(parseRetractionInput({ bookId }), { bookId });
});

Deno.test("normalizes UUID casing", () => {
  assertEquals(parseRetractionInput({ bookId: bookId.toUpperCase() }).bookId, bookId);
});

Deno.test("rejects unknown fields and owner/path spoofing", () => {
  for (const value of [
    { bookId, owner_id: ownerId },
    { bookId, storage_key: `${ownerId}/${bookId}.epub` },
    { bookId, extra: true },
  ]) {
    let thrown = false;
    try {
      parseRetractionInput(value);
    } catch (error) {
      thrown = error instanceof RetractionInputError;
    }
    assertEquals(thrown, true);
  }
});

Deno.test("rejects invalid UUID and non-object input", () => {
  for (const value of [null, [], "text", {}, { bookId: "../other.epub" }, { bookId: 123 }]) {
    let thrown = false;
    try {
      parseRetractionInput(value);
    } catch (error) {
      thrown = error instanceof RetractionInputError;
    }
    assertEquals(thrown, true);
  }
});

Deno.test("accepts only the database-generated owner/book storage key", () => {
  assertEquals(isExpectedStorageKey(ownerId, bookId, `${ownerId}/${bookId}.epub`), true);
  assertEquals(isExpectedStorageKey(ownerId, bookId, `${ownerId}/other.epub`), false);
  assertEquals(isExpectedStorageKey(ownerId, bookId, `other/${bookId}.epub`), false);
  assertEquals(isExpectedStorageKey(ownerId, bookId, "../other.epub"), false);
});
