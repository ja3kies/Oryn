export const RIGHTS_DECLARATION_VERSION = "oryn-epub-rights-v1";

export type CreateUploadInput = {
  title: string;
  author: string;
  language: string;
};

export class InputError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "InputError";
  }
}

function requiredText(value: unknown, field: string, maxLength: number): string {
  if (typeof value !== "string") {
    throw new InputError(`${field} must be a string`);
  }

  const normalized = value.trim();
  if (
    normalized.length === 0 ||
    normalized.length > maxLength ||
    /[\u0000-\u001f\u007f]/u.test(normalized)
  ) {
    throw new InputError(`${field} is invalid`);
  }

  return normalized;
}

/**
 * Strict allow-list: identity, storage paths, status and file metadata are
 * server-controlled. Unknown keys (including owner_id) are rejected.
 */
export function parseCreateUploadInput(value: unknown): CreateUploadInput {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    throw new InputError("Request body must be a JSON object");
  }

  const body = value as Record<string, unknown>;
  const allowedKeys = new Set(["title", "author", "language", "rightsConfirmed"]);
  for (const key of Object.keys(body)) {
    if (!allowedKeys.has(key)) {
      throw new InputError("Request contains unsupported fields");
    }
  }

  if (body.rightsConfirmed !== true) {
    throw new InputError("Explicit rights confirmation is required");
  }

  const title = requiredText(body.title, "title", 200);
  const author = requiredText(body.author, "author", 200);
  const language = requiredText(body.language, "language", 35);

  if (!/^[a-z]{2,3}(-[A-Za-z0-9]{2,8})*$/u.test(language)) {
    throw new InputError("language must be a valid BCP 47-style language tag");
  }

  return { title, author, language };
}
