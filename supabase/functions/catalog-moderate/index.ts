import { createClient } from "npm:@supabase/supabase-js@2.95.0";
import { corsHeaders } from "npm:@supabase/supabase-js@2.95.0/cors";
import {
  ModerationInputError,
  parseModerationInput,
  parseModeratorIds,
} from "./validation.ts";

const MAX_BODY_BYTES = 2048;

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

function bearerToken(request: Request): string | null {
  const value = request.headers.get("Authorization") ?? "";
  return /^Bearer\s+(.+)$/iu.exec(value.trim())?.[1] ?? null;
}

async function readSmallJson(request: Request): Promise<unknown> {
  if (!request.body) throw new Error("missing_body");
  const reader = request.body.getReader();
  const chunks: Uint8Array[] = [];
  let size = 0;

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    size += value.byteLength;
    if (size > MAX_BODY_BYTES) {
      await reader.cancel();
      throw new RangeError("body_too_large");
    }
    chunks.push(value);
  }

  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(bytes));
}

Deno.serve(async (request: Request): Promise<Response> => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (request.method !== "POST") return jsonResponse(405, { error: "method_not_allowed" });
  if (!/^application\/json(?:\s*;|$)/iu.test(request.headers.get("Content-Type") ?? "")) {
    return jsonResponse(415, { error: "content_type_must_be_json" });
  }

  const token = bearerToken(request);
  if (!token) return jsonResponse(401, { error: "authentication_required" });

  let input: { bookId: string; decision: "publish" | "reject" };
  try {
    input = parseModerationInput(await readSmallJson(request));
  } catch (error) {
    return jsonResponse(error instanceof RangeError ? 413 : 400, {
      error: error instanceof RangeError ? "request_too_large" :
        error instanceof ModerationInputError ? error.message : "invalid_json",
    });
  }

  let moderatorIds: Set<string>;
  try {
    moderatorIds = parseModeratorIds(Deno.env.get("CATALOG_MODERATOR_USER_IDS"));
  } catch {
    console.error("catalog-moderate has an invalid moderator allowlist configuration");
    return jsonResponse(503, { error: "service_unavailable" });
  }
  if (moderatorIds.size === 0) {
    console.error("catalog-moderate moderator allowlist is not configured");
    return jsonResponse(503, { error: "service_unavailable" });
  }

  const url = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !anonKey || !serviceKey) {
    console.error("catalog-moderate is missing server configuration");
    return jsonResponse(503, { error: "service_unavailable" });
  }

  const authClient = createClient(url, anonKey, {
    auth: { autoRefreshToken: false, persistSession: false, detectSessionInUrl: false },
  });
  const { data: authData, error: authError } = await authClient.auth.getUser(token);
  if (authError || !authData.user || authData.user.is_anonymous) {
    return jsonResponse(401, { error: "valid_user_session_required" });
  }

  // Authorization comes only from a server-controlled allowlist. Never trust
  // user_metadata, app-supplied roles, or moderator IDs from the request body.
  if (!moderatorIds.has(authData.user.id.toLowerCase())) {
    return jsonResponse(403, { error: "moderator_access_required" });
  }

  const admin = createClient(url, serviceKey, {
    auth: { autoRefreshToken: false, persistSession: false, detectSessionInUrl: false },
  });

  const update = input.decision === "publish"
    ? { status: "published", published_at: new Date().toISOString() }
    : { status: "rejected", published_at: null };

  // Conditional transition makes moderation single-shot and prevents a
  // concurrent retraction from being overwritten by a late publish decision.
  const { data: changed, error: updateError } = await admin
    .from("community_shared_books")
    .update(update)
    .eq("id", input.bookId)
    .eq("status", "pending")
    .not("sha256", "is", null)
    .is("removed_at", null)
    .select("id, status, published_at")
    .maybeSingle();

  if (updateError) {
    console.error("catalog-moderate conditional state transition failed");
    return jsonResponse(500, { error: "moderation_failed" });
  }
  if (!changed) {
    // Deliberately do not distinguish unknown IDs, unvalidated uploads,
    // already-decided records, or records that have been retracted.
    return jsonResponse(409, { error: "book_not_moderatable" });
  }

  return jsonResponse(200, {
    bookId: changed.id,
    status: changed.status,
    publishedAt: changed.published_at,
  });
});
