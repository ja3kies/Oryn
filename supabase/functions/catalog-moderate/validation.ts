const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/iu;

export type ModerationDecision = "publish" | "reject";

export class ModerationInputError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "ModerationInputError";
  }
}

export function parseModeratorIds(value: string | undefined): Set<string> {
  const ids = new Set<string>();
  if (!value?.trim()) return ids;

  for (const item of value.split(",")) {
    const candidate = item.trim();
    if (!candidate) continue;
    if (!UUID_PATTERN.test(candidate)) {
      throw new Error("CATALOG_MODERATOR_USER_IDS contains an invalid UUID");
    }
    ids.add(candidate.toLowerCase());
  }
  return ids;
}

export function parseModerationInput(value: unknown): {
  bookId: string;
  decision: ModerationDecision;
} {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new ModerationInputError("request_must_be_object");
  }

  const record = value as Record<string, unknown>;
  if (
    Object.keys(record).length !== 2 ||
    typeof record.bookId !== "string" ||
    !UUID_PATTERN.test(record.bookId) ||
    (record.decision !== "publish" && record.decision !== "reject")
  ) {
    throw new ModerationInputError("invalid_moderation_request");
  }

  return {
    bookId: record.bookId.toLowerCase(),
    decision: record.decision,
  };
}
