import {
  isExpectedDownloadReference,
  parseDownloadInput,
} from "./validation.ts";

const ownerId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
const bookId = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
const digest = "a".repeat(64);

Deno.test("accepts a single valid book UUID", () => {
  const result = parseDownloadInput({ bookId: bookId.toUpperCase() });
  if (result.bookId !== bookId) throw new Error("UUID should be normalized");
});

Deno.test("rejects missing, malformed and non-object input", () => {
  for (const value of [null, [], "{}", {}, { bookId: "not-a-uuid" }]) {
    let rejected = false;
    try {
      parseDownloadInput(value);
    } catch {
      rejected = true;
    }
    if (!rejected) throw new Error("invalid input was accepted");
  }
});

Deno.test("rejects client-supplied owner and storage path fields", () => {
  let rejected = false;
  try {
    parseDownloadInput({
      bookId,
      owner_id: ownerId,
      storage_key: `${ownerId}/${bookId}.epub`,
    });
  } catch {
    rejected = true;
  }
  if (!rejected) throw new Error("extra fields were accepted");
});

Deno.test("accepts only the exact database-generated owner/book path and SHA-256", () => {
  if (!isExpectedDownloadReference(ownerId, bookId, `${ownerId}/${bookId}.epub`, digest)) {
    throw new Error("expected reference rejected");
  }
  const invalid = [
    [ownerId, bookId, `${ownerId}/other.epub`, digest],
    [ownerId, bookId, `other/${bookId}.epub`, digest],
    [ownerId, bookId, `${ownerId}/${bookId}.epub`, null],
    [ownerId, bookId, `${ownerId}/${bookId}.epub`, "not-a-hash"],
  ] as const;
  for (const [owner, id, path, hash] of invalid) {
    if (isExpectedDownloadReference(owner, id, path, hash)) {
      throw new Error("invalid stored reference accepted");
    }
  }
});
