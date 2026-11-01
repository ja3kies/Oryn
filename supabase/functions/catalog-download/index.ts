import { createClient } from "npm:@supabase/supabase-js@2.95.0";
import { corsHeaders } from "npm:@supabase/supabase-js@2.95.0/cors";
import { DownloadInputError, isExpectedDownloadReference, parseDownloadInput } from "./validation.ts";

const BUCKET_ID = "community-epubs";
const MAX_FILE_BYTES = 8 * 1024 * 1024;
const MAX_BODY_BYTES = 2048;

function jsonResponse(status: number, body: Record<string, unknown>): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      ...corsHeaders,
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store, max-age=0",
      "Pragma": "no-cache",
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
    input = parseDownloadInput(await readSmallJson(request));
  } catch (error) {
    return jsonResponse(error instanceof RangeError ? 413 : 400, {
      error: error instanceof RangeError ? "request_too_large" :
        error instanceof DownloadInputError ? error.message : "invalid_json",
    });
  }

  const url = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !anonKey || !serviceKey) {
    console.error("catalog-download is missing server configuration");
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

  // Public catalogue eligibility is checked afresh for every download request.
  // The owner and object path are read only from the trusted database row.
  const { data: book, error: lookupError } = await admin
    .from("community_shared_books")
    .select("id, owner_id, storage_key, status, sha256, published_at, removed_at")
    .eq("id", input.bookId)
    .eq("status", "published")
    .not("sha256", "is", null)
    .not("published_at", "is", null)
    .is("removed_at", null)
    .maybeSingle();

  if (lookupError) {
    console.error("catalog-download published record lookup failed");
    return jsonResponse(500, { error: "could_not_load_book" });
  }
  // Same response for missing, pending, rejected and removed records.
  if (!book) return jsonResponse(404, { error: "published_book_not_found" });

  if (!isExpectedDownloadReference(book.owner_id, book.id, book.storage_key, book.sha256)) {
    console.error("catalog-download encountered an invalid stored book reference");
    return jsonResponse(500, { error: "invalid_stored_object_reference" });
  }

  // Stream the private object through this authenticated endpoint instead of
  // issuing a bearer signed URL that could outlive a catalogue retraction.
  const { data: file, error: downloadError } = await admin.storage
    .from(BUCKET_ID)
    .download(book.storage_key);

  if (downloadError || !file) {
    console.error("catalog-download could not retrieve the stored EPUB");
    return jsonResponse(503, { error: "download_temporarily_unavailable" });
  }
  if (file.size <= 0 || file.size > MAX_FILE_BYTES) {
    console.error("catalog-download encountered an object outside the configured size limit");
    return jsonResponse(422, { error: "stored_file_size_invalid" });
  }

  return new Response(file.stream(), {
    status: 200,
    headers: {
      ...corsHeaders,
      "Content-Type": "application/epub+zip",
      "Content-Disposition": 'attachment; filename="oryn-community-book.epub"',
      "Content-Length": String(file.size),
      "Cache-Control": "private, no-store, max-age=0",
      "Pragma": "no-cache",
      "X-Content-Type-Options": "nosniff",
      "Content-Security-Policy": "sandbox",
    },
  });
});
