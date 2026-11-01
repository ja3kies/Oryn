import { cleanupRetryDelayMs } from "./retry.ts";

Deno.test("cleanup retry backoff grows exponentially and is capped", () => {
  const expected = [5, 10, 20, 40, 80, 160, 320, 360];
  for (let i = 0; i < expected.length; i++) {
    const actual = cleanupRetryDelayMs(i + 1);
    if (actual !== expected[i] * 60_000) {
      throw new Error(`Unexpected delay for attempt ${i + 1}: ${actual}`);
    }
  }
});

Deno.test("cleanup retry backoff handles invalid attempt numbers safely", () => {
  if (cleanupRetryDelayMs(0) !== 5 * 60_000) throw new Error("Zero attempt must use first retry delay");
  if (cleanupRetryDelayMs(Number.NaN) !== 5 * 60_000) throw new Error("NaN attempt must use first retry delay");
});
