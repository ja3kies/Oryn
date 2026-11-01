import { createClient } from "npm:@supabase/supabase-js@2.95.0";
import { corsHeaders } from "npm:@supabase/supabase-js@2.95.0/cors";
import {
  InputError,
  parseCreateUploadInput,
  RIGHTS_DECLARATION_VERSION,
} from "./validation.ts";
import type { CreateUploadInput } from "./validation.ts";

const BUCKET_ID = "community-epubs";
const MAX_REQUEST_BYTES = 8 * 1024;
const MAX_FILE_BYTES = 8 * 1024 * 1024;

function jsonResponse(
  status: number,
  body: Record<string, unknown>,
): Response {
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

function getBearerToken(request: Request): string | null {
  const header = request.headers.get("Authorization");
  if (!header) return null;

  const match = /^Bearer\s+(.+)$/iu.exec(header.trim());
  return match?.[1] ?? null;
}

Deno.serve(async (request: Request): Promise<Response> => {
  if (request.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (request.method !== "POST") {
    return jsonResponse(405, { error: "method_not_allowed" });
  }

  const contentType = request.headers.get("Content-Type") ?? "";
  if (!/^application\/json(?:\s*;|$)/iu.test(contentType)) {
    return jsonResponse(415, { error: "content_type_must_be_json" });
  }

  const declaredLength = Number(request.headers.get("Content-Length") ?? "0");
  if (Number.isFinite(declaredLength) && declaredLength > MAX_REQUEST_BYTES) {
    return jsonResponse(413, { error: "request_too_large" });
  }

  const token = getBearerToken(request);
  if (!token) {
    return jsonResponse(401, { error: "authentication_required" });
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");

  if (!supabaseUrl || !anonKey || !serviceRoleKey) {
    console.error("catalog-create-upload is missing required server configuration");
    return jsonResponse(503, { error: "service_unavailable" });
  }

  // Enforce the body cap while streaming; Content-Length alone is not
  // trustworthy because clients can omit it or use chunked transfer encoding.
  let rawBody: string;
  try {
    if (!request.body) {
      return jsonResponse(400, { error: "invalid_request_body" });
    }

    const reader = request.body.getReader();
    const chunks: Uint8Array[] = [];
    let totalBytes = 0;

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      totalBytes += value.byteLength;
      if (totalBytes > MAX_REQUEST_BYTES) {
        await reader.cancel();
        return jsonResponse(413, { error: "request_too_large" });
      }
      chunks.push(value);
    }

    const combined = new Uint8Array(totalBytes);
    let offset = 0;
    for (const chunk of chunks) {
      combined.set(chunk, offset);
      offset += chunk.byteLength;
    }
    rawBody = new TextDecoder("utf-8", { fatal: true }).decode(combined);
  } catch {
    return jsonResponse(400, { error: "invalid_request_body" });
  }

  let parsedJson: unknown;
  try {
    parsedJson = JSON.parse(rawBody);
  } catch {
    return jsonResponse(400, { error: "invalid_json" });
  }

  let input: CreateUploadInput;
  try {
    input = parseCreateUploadInput(parsedJson);
  } catch (error) {
    if (error instanceof InputError) {
      return jsonResponse(400, { error: "invalid_input", message: error.message });
    }
    return jsonResponse(400, { error: "invalid_input" });
  }

  // Independently verify the bearer token with Supabase Auth. Never trust a
  // user ID or owner field supplied by the client.
  const authClient = createClient(supabaseUrl, anonKey, {
    auth: {
      autoRefreshToken: false,
      persistSession: false,
      detectSessionInUrl: false,
    },
  });

  const { data: authData, error: authError } = await authClient.auth.getUser(token);
  if (authError || !authData.user || authData.user.is_anonymous) {
    return jsonResponse(401, { error: "valid_user_session_required" });
  }

  // Privileged credentials remain inside this server function only.
  const adminClient = createClient(supabaseUrl, serviceRoleKey, {
    auth: {
      autoRefreshToken: false,
      persistSession: false,
      detectSessionInUrl: false,
    },
  });

  const { data: row, error: insertError } = await adminClient
    .from("community_shared_books")
    .insert({
      owner_id: authData.user.id,
      title: input.title,
      author: input.author,
      language: input.language,
      status: "pending",
      rights_declaration_version: RIGHTS_DECLARATION_VERSION,
      consent_at: new Date().toISOString(),
    })
    .select("id, storage_key, expires_at")
    .single();

  if (insertError || !row) {
    // The database trigger enforces per-owner limits atomically.
    if (insertError?.code === "P0001" && insertError.message?.startsWith("catalog_limit:")) {
      const limit = insertError.message.slice("catalog_limit:".length);
      return jsonResponse(429, {
        error: "catalog_upload_limit_reached",
        limit,
        retryable: limit === "hourly_creation",
      });
    }
    // Avoid returning database details or user data to the caller.
    console.error("catalog-create-upload could not create pending record");
    return jsonResponse(500, { error: "could_not_create_upload" });
  }

  return jsonResponse(201, {
    bookId: row.id,
    bucket: BUCKET_ID,
    objectPath: row.storage_key,
    uploadExpiresAt: row.expires_at,
    maxFileBytes: MAX_FILE_BYTES,
    status: "pending",
    nextStep: "upload_epub_with_authenticated_user_session",
  });
});
