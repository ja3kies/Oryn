/** Development microbenchmark of rejected archive directories, never an Edge/backend call.
 * Optional first argument: file:// URL of a baseline validation module exported from Git.
 */
import { zipSync } from "npm:fflate@0.8.2";
const source = Deno.args[0] ? new URL(Deno.args[0])
  : new URL("../supabase/functions/catalog-finalize-upload/validation.ts", import.meta.url);
if (source.protocol !== "file:") throw new Error("Only local validation modules may be benchmarked");
const { validateEpub } = await import(source.href);
const entries: Record<string, Uint8Array> = {};
for (let i = 0; i < 5000; i++) entries[`part-${i}.bin`] = new Uint8Array();
const bytes = zipSync(entries, { level: 0 });
const blob = new Blob([new Uint8Array(bytes).buffer]);
const samples: number[] = [];
for (let i = 0; i < 23; i++) {
  const start = performance.now();
  try { await validateEpub(blob); throw new Error("Invalid archive accepted"); }
  catch (error) {
    if (!(error instanceof Error) || error.message !== "archive_entry_count_out_of_range") throw error;
  }
  if (i >= 3) samples.push(performance.now() - start);
}
samples.sort((a, b) => a - b);
console.log(JSON.stringify({ fixtureBytes: bytes.length, entries: 5000, warmup: 3, samples: 20,
  medianMillis: (samples[9] + samples[10]) / 2, p95Millis: samples[18] }));
