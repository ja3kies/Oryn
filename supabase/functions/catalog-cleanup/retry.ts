const RETRY_BASE_MS = 5 * 60 * 1000;
const RETRY_MAX_MS = 6 * 60 * 60 * 1000;

/** Exponential backoff capped at six hours. Attempt numbers are one-based. */
export function cleanupRetryDelayMs(attempt: number): number {
  const safeAttempt = Number.isFinite(attempt) ? Math.max(1, Math.floor(attempt)) : 1;
  return Math.min(RETRY_BASE_MS * (2 ** Math.min(safeAttempt - 1, 16)), RETRY_MAX_MS);
}
