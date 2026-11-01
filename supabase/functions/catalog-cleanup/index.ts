import { createClient } from "npm:@supabase/supabase-js@2.95.0";
import { corsHeaders } from "npm:@supabase/supabase-js@2.95.0/cors";

const BUCKET_ID = "community-epubs";
const BATCH_SIZE = 50;
const EXPIRY_GRACE_MS = 15 * 60 * 1000;
const RETRY_BASE_MS = 5 * 60 * 1000;
const RETRY_MAX_MS = 6 * 60 * 60 * 1000;

export function cleanupRetryDelayMs(attempt: number): number {
  const safeAttempt = Math.max(1, Math.floor(attempt));
  return Math.min(RETRY_BASE_MS * (2 ** Math.min(safeAttempt - 1, 16)), RETRY_MAX_MS);
}

function jsonResponse(status: number, body: Record<string, unknown>): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      ...corsHeaders,
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      "X-Content-Type-Options": "nosniff",
    },
  });
}

function constantTimeEqual(left: string, right: string): boolean {
  const length = Math.max(left.length, right.length);
  let mismatch = left.length ^ right.length;
  for (let i = 0; i < length; i++) {
    mismatch |= (left.charCodeAt(i) || 0) ^ (right.charCodeAt(i) || 0);
  }
  return mismatch === 0;
}

Deno.serve(async (request: Request): Promise<Response> => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (request.method !== "POST") return jsonResponse(405, { error: "method_not_allowed" });

  const configuredSecret = Deno.env.get("CATALOG_CLEANUP_SECRET");
  const suppliedSecret = request.headers.get("x-catalog-cleanup-secret") ?? "";
  if (!configuredSecret || configuredSecret.length < 32 ||
      !constantTimeEqual(suppliedSecret, configuredSecret)) {
    return jsonResponse(401, { error: "unauthorized" });
  }

  const url = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !serviceKey) {
    console.error("catalog-cleanup is missing server configuration");
    return jsonResponse(503, { error: "service_unavailable" });
  }

  const admin = createClient(url, serviceKey, {
    auth: { autoRefreshToken: false, persistSession: false, detectSessionInUrl: false },
  });
  const now = new Date().toISOString();
  const expiryCutoff = new Date(Date.now() - EXPIRY_GRACE_MS).toISOString();

  // First make expired uploads invisible and non-uploadable. The Storage
  // policy permits inserts only while the associated row remains pending.
  const { data: expiryCandidates, error: candidateError } = await admin
    .from("community_shared_books")
    .select("id")
    .eq("status", "pending")
    .not("expires_at", "is", null)
    .lt("expires_at", expiryCutoff)
    .order("expires_at", { ascending: true })
    .limit(BATCH_SIZE);

  if (candidateError) {
    console.error("catalog-cleanup could not load overdue uploads");
    return jsonResponse(500, { error: "cleanup_failed" });
  }

  let newlyExpired: Array<{ id: string }> = [];
  if (expiryCandidates?.length) {
    const { data, error } = await admin
      .from("community_shared_books")
      .update({ status: "expired", expired_at: now })
      .in("id", expiryCandidates.map((book) => book.id))
      .eq("status", "pending")
      .lt("expires_at", expiryCutoff)
      .select("id");

    if (error) {
      console.error("catalog-cleanup could not expire abandoned uploads");
      return jsonResponse(500, { error: "cleanup_failed" });
    }
    newlyExpired = data ?? [];
  }

  // Also retry earlier expirations whose Storage deletion failed. A bounded
  // batch prevents a single scheduled invocation from monopolizing resources.
  const { data: pendingCleanup, error: lookupError } = await admin
    .from("community_shared_books")
    .select("id, owner_id, storage_key, storage_cleanup_attempts")
    .eq("status", "expired")
    .is("storage_cleanup_at", null)
    .or(`storage_cleanup_retry_at.is.null,storage_cleanup_retry_at.lte.${now}`)
    // Never-attempted rows are handled first. Failed rows are deferred until
    // their retry time, so a bad object cannot starve the rest of the queue.
    .order("storage_cleanup_retry_at", { ascending: true, nullsFirst: true })
    .order("expired_at", { ascending: true })
    .limit(BATCH_SIZE);

  if (lookupError) {
    console.error("catalog-cleanup could not load expired uploads");
    return jsonResponse(500, { error: "cleanup_failed" });
  }

  let deleted = 0;
  let failed = 0;
  for (const book of pendingCleanup ?? []) {
    const nextAttempt = (book.storage_cleanup_attempts ?? 0) + 1;
    const scheduleRetry = async () => {
      const retryAt = new Date(Date.now() + cleanupRetryDelayMs(nextAttempt)).toISOString();
      const { error } = await admin
        .from("community_shared_books")
        .update({
          storage_cleanup_attempts: nextAttempt,
          storage_cleanup_retry_at: retryAt,
        })
        .eq("id", book.id)
        .eq("status", "expired")
        .is("storage_cleanup_at", null);
      if (error) console.error("catalog-cleanup could not schedule a retry");
    };

    if (typeof book.storage_key !== "string" ||
        book.storage_key !== `${book.owner_id}/${book.id}.epub`) {
      failed++;
      console.error("catalog-cleanup encountered an invalid stored object key");
      await scheduleRetry();
      continue;
    }

    const { error: removeError } = await admin.storage
      .from(BUCKET_ID)
      .remove([book.storage_key]);

    if (removeError) {
      failed++;
      console.error("catalog-cleanup could not remove an expired object");
      await scheduleRetry();
      continue;
    }

    const { error: markError } = await admin
      .from("community_shared_books")
      .update({
        storage_cleanup_at: new Date().toISOString(),
        storage_cleanup_retry_at: null,
      })
      .eq("id", book.id)
      .eq("status", "expired")
      .is("storage_cleanup_at", null);

    if (markError) {
      failed++;
      console.error("catalog-cleanup could not record object cleanup");
      await scheduleRetry();
    } else {
      deleted++;
    }
  }

  return jsonResponse(failed > 0 ? 202 : 200, {
    expired: newlyExpired?.length ?? 0,
    cleaned: deleted,
    retryPending: failed,
    batchLimit: BATCH_SIZE,
  });
});
