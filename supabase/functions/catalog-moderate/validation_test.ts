import { assertEquals, assertThrows } from "jsr:@std/assert@1.0.14";
import {
  ModerationInputError,
  parseModerationInput,
  parseModeratorIds,
} from "./validation.ts";

const bookId = "987e6543-e21b-42d3-a456-426614174000";
const moderatorId = "123e4567-e89b-42d3-a456-426614174000";

Deno.test("parses a publish decision", () => {
  assertEquals(parseModerationInput({ bookId, decision: "publish" }), {
    bookId,
    decision: "publish",
  });
});

Deno.test("parses a reject decision and normalizes UUID casing", () => {
  assertEquals(parseModerationInput({
    bookId: bookId.toUpperCase(),
    decision: "reject",
  }), { bookId, decision: "reject" });
});

Deno.test("rejects unknown fields and client-supplied ownership or paths", () => {
  for (const value of [
    { bookId, decision: "publish", owner_id: moderatorId },
    { bookId, decision: "publish", storage_key: "x/y.epub" },
    { bookId, decision: "publish", moderator: true },
  ]) {
    assertThrows(() => parseModerationInput(value), ModerationInputError);
  }
});

Deno.test("rejects invalid decisions, UUIDs and non-object input", () => {
  for (const value of [
    null,
    [],
    "text",
    { bookId, decision: "delete" },
    { bookId: "../other.epub", decision: "publish" },
    { bookId, decision: "publish", extra: true },
  ]) {
    assertThrows(() => parseModerationInput(value), ModerationInputError);
  }
});

Deno.test("parses and deduplicates server-side moderator UUIDs", () => {
  assertEquals(
    [...parseModeratorIds(` ${moderatorId},${moderatorId.toUpperCase()} `)],
    [moderatorId],
  );
  assertEquals(parseModeratorIds(undefined).size, 0);
  assertEquals(parseModeratorIds("  ").size, 0);
});

Deno.test("rejects malformed moderator allowlist configuration", () => {
  assertThrows(
    () => parseModeratorIds(`${moderatorId},not-a-uuid`),
    Error,
    "CATALOG_MODERATOR_USER_IDS contains an invalid UUID",
  );
});
