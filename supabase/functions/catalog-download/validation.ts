const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/iu;
const SHA256_PATTERN = /^[a-f0-9]{64}$/iu;

export class DownloadInputError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "DownloadInputError";
  }
}

export function parseDownloadInput(value: unknown): { bookId: string } {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new DownloadInputError("request_must_be_object");
  }
  const record = value as Record<string, unknown>;
  if (Object.keys(record).length !== 1 || typeof record.bookId !== "string" ||
      !UUID_PATTERN.test(record.bookId)) {
    throw new DownloadInputError("invalid_book_id");
  }
  return { bookId: record.bookId.toLowerCase() };
}

export function isExpectedDownloadReference(
  ownerId: string,
  bookId: string,
  storageKey: string,
  sha256: string | null,
): boolean {
  return UUID_PATTERN.test(ownerId) &&
    UUID_PATTERN.test(bookId) &&
    storageKey === `${ownerId}/${bookId}.epub` &&
    typeof sha256 === "string" &&
    SHA256_PATTERN.test(sha256);
}
