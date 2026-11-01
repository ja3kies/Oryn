import { ModerationInputError, parseModerationInput, parseModeratorIds } from "./validation.ts";

const moderator = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
const book = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";

Deno.test("moderation accepts only publish/reject for one valid book UUID", () => {
  const parsed = parseModerationInput({ bookId: book.toUpperCase(), decision: "publish" });
  if (parsed.bookId !== book || parsed.decision !== "publish") throw new Error("Input not normalized");
  for (const value of [null, { bookId: book, decision: "delete" },
    { bookId: "bad", decision: "reject" }, { bookId: book, decision: "reject", owner_id: moderator }]) {
    let rejected = false;
    try { parseModerationInput(value); } catch (error) {
      rejected = error instanceof ModerationInputError;
    }
    if (!rejected) throw new Error("Invalid moderation input accepted");
  }
});

Deno.test("moderator allowlist normalizes IDs and rejects malformed configuration", () => {
  const ids = parseModeratorIds(` ${moderator.toUpperCase()}, ${moderator} ,, `);
  if (ids.size !== 1 || !ids.has(moderator)) throw new Error("Allowlist not normalized/deduplicated");
  if (parseModeratorIds(undefined).size !== 0) throw new Error("Missing allowlist must be empty");
  let rejected = false;
  try { parseModeratorIds(`${moderator},not-a-uuid`); } catch { rejected = true; }
  if (!rejected) throw new Error("Malformed allowlist accepted");
});
