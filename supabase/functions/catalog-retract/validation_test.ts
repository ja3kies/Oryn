import { isExpectedStorageKey, parseRetractionInput, RetractionInputError } from "./validation.ts";

const owner = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
const book = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";

Deno.test("retraction accepts only one valid book UUID", () => {
  if (parseRetractionInput({ bookId: book.toUpperCase() }).bookId !== book) {
    throw new Error("UUID was not normalized");
  }
  for (const value of [null, [], { bookId: "bad" }, { bookId: book, owner_id: owner }]) {
    let rejected = false;
    try { parseRetractionInput(value); } catch (error) {
      rejected = error instanceof RetractionInputError;
    }
    if (!rejected) throw new Error("Invalid retraction input accepted");
  }
});

Deno.test("retraction storage key must match the database identity", () => {
  if (!isExpectedStorageKey(owner, book, `${owner}/${book}.epub`)) {
    throw new Error("Valid storage key rejected");
  }
  for (const path of [`${owner}/other.epub`, `${owner}/${book}.zip`, `../${book}.epub`]) {
    if (isExpectedStorageKey(owner, book, path)) throw new Error("Invalid storage key accepted");
  }
});
