import { createClient } from "npm:@supabase/supabase-js@2.95.0";
import { corsHeaders } from "npm:@supabase/supabase-js@2.95.0/cors";
import { EpubValidationError, validateEpub } from "./validation.ts";

const BUCKET_ID = "community-epubs";
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/iu;
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

  let body: unknown;
  try {
    body = await readSmallJson(request);
  } catch (error) {
    return jsonResponse(error instanceof RangeError ? 413 : 400, {
      error: error instanceof RangeError ? "request_too_large" : "invalid_json",
    });
  }

  if (!body || typeof body !== "object" || Array.isArray(body) ||
      Object.keys(body).length !== 1 ||
      typeof (body as Record<string, unknown>).bookId !== "string" ||
      !UUID_PATTERN.test((body as Record<string, string>).bookId)) {
    return jsonResponse(400, { error: "invalid_book_id" });
  }
  const bookId = (body as Record<string, string>).bookId;

  const url = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !anonKey || !serviceKey) {
    console.error("catalog-finalize-upload is missing server configuration");
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
  const { data: book, error: lookupError } = await admin
    .from("community_shared_books")
    .select("id, owner_id, storage_key, status, sha256")
    .eq("id", bookId)
    .eq("owner_id", authData.user.id)
    .maybeSingle();

  if (lookupError) {
    console.error("catalog-finalize-upload record lookup failed");
    return jsonResponse(500, { error: "could_not_load_upload" });
  }
  if (!book) return jsonResponse(404, { error: "upload_not_found" });
  if (book.status !== "pending") return jsonResponse(409, { error: "upload_not_pending" });
  if (book.sha256) {
    return jsonResponse(200, { bookId, status: "pending", validated: true, sha256: book.sha256 });
  }

  // Never accept a Storage path from the caller: use the owner-scoped row.
  const { data: file, error: downloadError } = await admin.storage
    .from(BUCKET_ID)
    .download(book.storage_key);
  if (downloadError || !file) return jsonResponse(409, { error: "uploaded_file_not_found" });

  try {
    const sha256 = await validateEpub(file);
    const { data: updated, error: updateError } = await admin
      .from("community_shared_books")
      .update({ sha256, expires_at: null })
      .eq("id", book.id)
      .eq("owner_id", authData.user.id)
      .eq("status", "pending")
      .is("sha256", null)
      .select("id")
      .maybeSingle();

    if (updateError) {
      console.error("catalog-finalize-upload hash update failed");
      return jsonResponse(500, { error: "could_not_finalize_upload" });
    }
    if (!updated) return jsonResponse(409, { error: "upload_state_changed_retry" });

    // A successful structural check is not publication; moderation is separate.
    return jsonResponse(200, {
      bookId,
      status: "pending",
      validated: true,
      sha256,
      nextStep: "await_moderation",
    });
  } catch (error) {
    if (error instanceof EpubValidationError) {
      return jsonResponse(422, { error: "invalid_epub", reason: error.message });
    }
    console.error("catalog-finalize-upload failed during EPUB validation");
    return jsonResponse(422, { error: "invalid_or_unreadable_epub" });
  }
});
