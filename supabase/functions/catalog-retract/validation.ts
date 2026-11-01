const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/iu;

export class RetractionInputError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "RetractionInputError";
  }
}

export function parseRetractionInput(value: unknown): { bookId: string } {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new RetractionInputError("request_must_be_object");
  }

  const record = value as Record<string, unknown>;
  if (Object.keys(record).length !== 1 || typeof record.bookId !== "string" ||
      !UUID_PATTERN.test(record.bookId)) {
    throw new RetractionInputError("invalid_book_id");
  }

  return { bookId: record.bookId.toLowerCase() };
}

export function isExpectedStorageKey(ownerId: string, bookId: string, storageKey: string): boolean {
  return storageKey === `${ownerId}/${bookId}.epub`;
}
