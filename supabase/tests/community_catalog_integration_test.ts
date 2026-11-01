import { createClient, type SupabaseClient } from "npm:@supabase/supabase-js@2.95.0";

const url = Deno.env.get("SUPABASE_URL") ?? "";
const anonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const BUCKET = "community-epubs";

function assert(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message);
}

function assertEqual(actual: unknown, expected: unknown, message: string): void {
  if (actual !== expected) throw new Error(`${message}: expected ${String(expected)}, got ${String(actual)}`);
}

function makeClient(key: string): SupabaseClient {
  return createClient(url, key, {
    auth: { autoRefreshToken: false, persistSession: false, detectSessionInUrl: false },
  });
}

async function createTestUser(admin: SupabaseClient, label: string) {
  const email = `oryn-${label}-${crypto.randomUUID()}@example.test`;
  const password = `Oryn-Test-${crypto.randomUUID()}-Aa9!`;
  const { data, error } = await admin.auth.admin.createUser({
    email, password, email_confirm: true,
  });
  if (error || !data.user) throw new Error(`Could not create test user: ${error?.message ?? "missing user"}`);

  const client = makeClient(anonKey);
  const { data: session, error: signInError } = await client.auth.signInWithPassword({ email, password });
  if (signInError || !session.user || !session.session) {
    await admin.auth.admin.deleteUser(data.user.id);
    throw new Error(`Could not sign in test user: ${signInError?.message ?? "missing session"}`);
  }
  return { id: data.user.id, client, accessToken: session.session.access_token };
}

async function insertBook(
  admin: SupabaseClient,
  ownerId: string,
  status: "pending" | "published" | "rejected",
  suffix: string,
  createdAt?: string,
) {
  const { data, error } = await admin.from("community_shared_books").insert({
    owner_id: ownerId,
    ...(createdAt ? { created_at: createdAt } : {}),
    title: `Integration ${suffix}`,
    author: "Oryn Test",
    language: "en",
    status,
    rights_declaration_version: "oryn-epub-rights-v1",
    ...(status === "published" ? { published_at: new Date().toISOString(), sha256: "a".repeat(64) } : {}),
  }).select("id, storage_key").single();
  if (error || !data) throw new Error(`Could not seed ${status} book: ${error?.message ?? "missing row"}`);
  return data as { id: string; storage_key: string };
}

Deno.test({
  name: "community catalogue Auth, RLS, Storage isolation and quotas",
  sanitizeResources: false,
  sanitizeOps: false,
  fn: async () => {
    assert(url && anonKey && serviceKey, "Local Supabase environment variables are required");
    const admin = makeClient(serviceKey);
    const users: Array<{ id: string; client: SupabaseClient }> = [];
    const uploaded: Array<{ ownerId: string; key: string }> = [];

    try {
      const a = await createTestUser(admin, "owner");
      const b = await createTestUser(admin, "other");
      const hourly = await createTestUser(admin, "hourly");
      const active = await createTestUser(admin, "active");
      const pending = await createTestUser(admin, "pending");
      const cleanup = await createTestUser(admin, "cleanup");
      users.push(a, b, hourly, active, pending, cleanup);

      // RLS: owner sees own pending/rejected rows; another user sees published rows only.
      const ownPending = await insertBook(admin, a.id, "pending", "owner-pending");
      const ownRejected = await insertBook(admin, a.id, "rejected", "owner-rejected");
      const publicBook = await insertBook(admin, a.id, "published", "public");
      const { data: aRows, error: aReadError } = await a.client
        .from("community_shared_books").select("id,title,status").in("id", [ownPending.id, ownRejected.id, publicBook.id]);
      assert(!aReadError, `Owner metadata read failed: ${aReadError?.message}`);
      assertEqual(aRows?.length, 3, "Owner should see own rows and published row");

      const { data: bRows, error: bReadError } = await b.client
        .from("community_shared_books").select("id,title,status").in("id", [ownPending.id, ownRejected.id, publicBook.id]);
      assert(!bReadError, `Other-user metadata read failed: ${bReadError?.message}`);
      assertEqual(bRows?.length, 1, "Other user must see only the published row");
      assertEqual(bRows?.[0]?.id, publicBook.id, "Visible row must be the published book");

      // Client-side table writes must remain unavailable even to the owner.
      const { error: directInsertError } = await a.client.from("community_shared_books").insert({
        owner_id: a.id, title: "Forged", author: "Forged", language: "en",
        rights_declaration_version: "oryn-epub-rights-v1",
      });
      assert(directInsertError, "Authenticated client must not insert catalogue metadata directly");

      // Storage INSERT: owner can upload only to the exact pending key.
      const payload = new Blob(["not-a-real-epub; storage policy test"], { type: "application/epub+zip" });
      const { error: ownerUploadError } = await a.client.storage.from(BUCKET)
        .upload(ownPending.storage_key, payload, { contentType: "application/epub+zip", upsert: false });
      assert(!ownerUploadError, `Owner upload to own pending path failed: ${ownerUploadError?.message}`);
      uploaded.push({ ownerId: a.id, key: ownPending.storage_key });

      const { error: crossUserUploadError } = await b.client.storage.from(BUCKET)
        .upload(`${a.id}/${crypto.randomUUID()}.epub`, payload, { contentType: "application/epub+zip" });
      assert(crossUserUploadError, "Another user must not upload into the owner's namespace");

      const { data: forbiddenDownload, error: crossUserDownloadError } = await b.client.storage
        .from(BUCKET).download(ownPending.storage_key);
      assert(!forbiddenDownload && crossUserDownloadError, "Another user must not download a private EPUB object");

      // Storage may return HTTP 200 for an RLS-filtered DELETE that affects zero rows.
      // Verify the object still exists instead of relying on the response error shape.
      await a.client.storage.from(BUCKET).remove([ownPending.storage_key]);
      const { data: retainedObject, error: retainedObjectError } = await admin.storage
        .from(BUCKET).download(ownPending.storage_key);
      assert(!retainedObjectError && retainedObject, "Owner's direct delete request must not remove the Storage object");


      // Exercise the real cleanup Edge Function against an expired pending upload.
      const expiredAt = new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString();
      const { data: expiredBook, error: expiredInsertError } = await admin
        .from("community_shared_books")
        .insert({
          owner_id: cleanup.id,
          created_at: expiredAt,
          expires_at: expiredAt,
          title: "Expired integration upload",
          author: "Oryn Test",
          language: "en",
          status: "pending",
          rights_declaration_version: "oryn-epub-rights-v1",
        })
        .select("id, storage_key")
        .single();
      assert(!expiredInsertError && expiredBook, `Could not seed expired upload: ${expiredInsertError?.message}`);

      const expiredPayload = new Blob(["expired cleanup integration object"], {
        type: "application/epub+zip",
      });
      const { error: expiredUploadError } = await cleanup.client.storage.from(BUCKET)
        .upload(expiredBook.storage_key, expiredPayload, {
          contentType: "application/epub+zip",
          upsert: false,
        });
      assert(!expiredUploadError, `Could not upload expired fixture: ${expiredUploadError?.message}`);

      const functionsUrl = Deno.env.get("FUNCTIONS_URL") ?? "";
      const cleanupSecret = Deno.env.get("CATALOG_CLEANUP_SECRET") ?? "";
      assert(functionsUrl && cleanupSecret.length >= 32, "Local Edge Function test configuration is required");

      const deniedCleanup = await fetch(`${functionsUrl}/catalog-cleanup`, {
        method: "POST",
        headers: { apikey: anonKey },
      });
      assertEqual(deniedCleanup.status, 401, "Cleanup must reject requests without its secret");

      const cleanupResponse = await fetch(`${functionsUrl}/catalog-cleanup`, {
        method: "POST",
        headers: { apikey: anonKey, "x-catalog-cleanup-secret": cleanupSecret },
      });
      assertEqual(cleanupResponse.status, 200, "Cleanup should expire and remove an overdue upload");

      const { data: expiredState, error: expiredStateError } = await admin
        .from("community_shared_books")
        .select("status, expired_at, storage_cleanup_at")
        .eq("id", expiredBook.id)
        .single();
      assert(!expiredStateError && expiredState, "Expired upload state must remain readable to the service role");
      assertEqual(expiredState.status, "expired", "Cleanup must transition pending upload to expired");
      assert(expiredState.expired_at && expiredState.storage_cleanup_at, "Cleanup must record expiry and successful object deletion");

      const { data: deletedObject, error: deletedObjectError } = await admin.storage
        .from(BUCKET).download(expiredBook.storage_key);
      assert(!deletedObject && deletedObjectError, "Cleanup must delete the expired Storage object");

      // The hourly limit counts rejected rows too: 10 succeed, the 11th is rejected.
      for (let i = 0; i < 10; i++) await insertBook(admin, hourly.id, "rejected", `hourly-${i}`);
      const { error: hourlyLimitError } = await admin.from("community_shared_books").insert({
        owner_id: hourly.id, title: "Hourly overflow", author: "Test", language: "en",
        status: "rejected", rights_declaration_version: "oryn-epub-rights-v1",
      });
      assert(hourlyLimitError, "The 11th creation within an hour must be rejected");

      // Three pending uploads are allowed; the fourth is rejected.
      for (let i = 0; i < 3; i++) await insertBook(admin, pending.id, "pending", `pending-${i}`);
      const { error: pendingLimitError } = await admin.from("community_shared_books").insert({
        owner_id: pending.id, title: "Pending overflow", author: "Test", language: "en",
        status: "pending", rights_declaration_version: "oryn-epub-rights-v1",
      });
      assert(pendingLimitError, "The fourth pending upload must be rejected");

      // Age these fixtures so the independent one-hour creation quota does not
      // prevent reaching the 30-book active quota in a single test run.
      const olderThanHour = new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString();
      // Thirty active books are allowed; the 31st is rejected.
      for (let i = 0; i < 30; i++) {
        await insertBook(admin, active.id, "published", `active-${i}`, olderThanHour);
      }
      const { error: activeLimitError } = await admin.from("community_shared_books").insert({
        owner_id: active.id, title: "Active overflow", author: "Test", language: "en",
        status: "published", published_at: new Date().toISOString(), sha256: "b".repeat(64),
        rights_declaration_version: "oryn-epub-rights-v1",
      });
      assert(activeLimitError, "The 31st active book must be rejected");
    } finally {
      for (const item of uploaded) {
        await admin.storage.from(BUCKET).remove([item.key]);
      }
      for (const user of users) {
        await admin.auth.admin.deleteUser(user.id);
      }
    }
  },
});
