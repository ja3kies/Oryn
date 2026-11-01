import { createClient } from "npm:@supabase/supabase-js@2.95.0";
import { corsHeaders } from "npm:@supabase/supabase-js@2.95.0/cors";
import { EpubValidationError, validateEpub } from "./validation.ts";

const BUCKET_ID = "community-epubs";
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/iu;

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
    const raw = await request.text();
    if (new TextEncoder().encode(raw).byteLength > 2048) {
      return jsonResponse(413, { error: "request_too_large" });
    }
    body = JSON.parse(raw);
  } catch {
    return jsonResponse(400, { error: "invalid_json" });
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

  // The path comes only from the owner-scoped database row, never from input.
  const { data: file, error: downloadError } = await admin.storage
    .from(BUCKET_ID)
    .download(book.storage_key);

  if (downloadError || !file) {
    return jsonResponse(409, { error: "uploaded_file_not_found" });
  }

  try {
    const sha256 = await validateEpub(file);
    const { data: updated, error: updateError } = await admin
      .from("community_shared_books")
      .update({ sha256 })
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
    if (!updated) {
      return jsonResponse(409, { error: "upload_state_changed_retry" });
    }

    // Validation does not publish the book. Moderation remains a separate,
    // privileged operation; the row deliberately stays pending.
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
