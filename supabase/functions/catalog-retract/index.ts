import { createClient } from "npm:@supabase/supabase-js@2.95.0";
import { corsHeaders } from "npm:@supabase/supabase-js@2.95.0/cors";
import {
  isExpectedStorageKey,
  parseRetractionInput,
  RetractionInputError,
} from "./validation.ts";

const BUCKET_ID = "community-epubs";
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

  let input: { bookId: string };
  try {
    input = parseRetractionInput(await readSmallJson(request));
  } catch (error) {
    return jsonResponse(error instanceof RangeError ? 413 : 400, {
      error: error instanceof RangeError ? "request_too_large" :
        error instanceof RetractionInputError ? error.message : "invalid_json",
    });
  }

  const url = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !anonKey || !serviceKey) {
    console.error("catalog-retract is missing server configuration");
    return jsonResponse(503, { error: "service_unavailable" });
  }

  const authClient = createClient(url, anonKey, {
    auth: { autoRefreshToken: false, persistSession: false, detectSessionInUrl: false },
  });
  const { data: authData, error: authError } = await authClient.auth.getUser(token);
  if (authError || !authData.user || authData.user.is_anonymous) {
    return jsonResponse(401, { error: "valid_user_session_required" });
  }

  const admin = createClient(url, serviceKey, {
    auth: { autoRefreshToken: false, persistSession: false, detectSessionInUrl: false },
  });

  // Scope the lookup to the verified caller; never accept owner_id or a path
  // from the request body.
  const { data: book, error: lookupError } = await admin
    .from("community_shared_books")
    .select("id, owner_id, storage_key, status, removed_at")
    .eq("id", input.bookId)
    .eq("owner_id", authData.user.id)
    .maybeSingle();

  if (lookupError) {
    console.error("catalog-retract record lookup failed");
    return jsonResponse(500, { error: "could_not_load_book" });
  }
  if (!book) return jsonResponse(404, { error: "book_not_found" });

  if (!isExpectedStorageKey(book.owner_id, book.id, book.storage_key)) {
    console.error("catalog-retract encountered an unexpected generated storage key");
    return jsonResponse(500, { error: "invalid_stored_object_reference" });
  }

  // Remove from the catalogue first. This immediately prevents further
  // catalogue discovery/download authorization before object cleanup begins.
  if (book.status !== "removed") {
    const { data: removed, error: updateError } = await admin
      .from("community_shared_books")
      .update({ status: "removed", removed_at: new Date().toISOString() })
      .eq("id", book.id)
      .eq("owner_id", authData.user.id)
      .in("status", ["pending", "published", "rejected"])
      .select("id")
      .maybeSingle();

    if (updateError) {
      console.error("catalog-retract status update failed");
      return jsonResponse(500, { error: "could_not_retract_book" });
    }

    if (!removed) {
      // A concurrent retry may already have completed the state transition.
      const { data: latest, error: latestError } = await admin
        .from("community_shared_books")
        .select("status")
        .eq("id", book.id)
        .eq("owner_id", authData.user.id)
        .maybeSingle();
      if (latestError) {
        console.error("catalog-retract state recheck failed");
        return jsonResponse(500, { error: "could_not_confirm_retraction" });
      }
      if (!latest || latest.status !== "removed") {
        return jsonResponse(409, { error: "book_state_changed_retry" });
      }
    }
  }

  // Retain the removed metadata row as a tombstone. Deletion is idempotent:
  // if a prior request removed the object but failed before responding, retry.
  const { error: removeError } = await admin.storage
    .from(BUCKET_ID)
    .remove([book.storage_key]);

  if (removeError) {
    console.error("catalog-retract object cleanup failed");
    return jsonResponse(202, {
      bookId: book.id,
      status: "removed",
      objectDeleted: false,
      cleanupPending: true,
      retryable: true,
    });
  }

  return jsonResponse(200, {
    bookId: book.id,
    status: "removed",
    objectDeleted: true,
    cleanupPending: false,
  });
});
