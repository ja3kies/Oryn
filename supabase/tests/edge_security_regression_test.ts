import { strToU8, zipSync } from "npm:fflate@0.8.2";

type Handler = (request: Request) => Response | Promise<Response>;
const handlers = new Map<string, Handler>();
const owner = "10000000-0000-4000-8000-000000000001";
const bookId = "20000000-0000-4000-8000-000000000002";
const sha256 = "a".repeat(64);
const book = { id: bookId, owner_id: owner, storage_key: `${owner}/${bookId}.epub`,
  status: "published", sha256, published_at: "2026-01-01T00:00:00Z", removed_at: null };
const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), {
  status, headers: { "Content-Type": "application/json" },
});
function assert(value: unknown, message: string) { if (!value) throw new Error(message); }
function epub() {
  return zipSync({
    "mimetype": strToU8("application/epub+zip"),
    "META-INF/container.xml": strToU8('<container><rootfiles><rootfile full-path="OPS/book.opf" media-type="application/oebps-package+xml"/></rootfiles></container>'),
    "OPS/book.opf": strToU8('<package><metadata><title>Test</title><creator>Test</creator></metadata><manifest><item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="chapter"/></spine></package>'),
    "OPS/chapter.xhtml": strToU8('<html xmlns="http://www.w3.org/1999/xhtml"><body>Test fixture only</body></html>'),
  }, { level: 0 });
}

/** Actual handlers + real SDK; all outbound fetches intercepted. No listener, env secret or network.
 * Global substitutions are restored per test; tests in this file run sequentially.
 */
async function run(name: string, backend: (request: Request) => Response | Promise<Response>,
  check: (response: Response) => void | Promise<void>, headers: Record<string, string> = {}) {
  const serve = Deno.serve;
  const getEnv = Deno.env.get;
  const fetch = globalThis.fetch;
  try {
    Deno.env.get = (key: string) => ({ SUPABASE_URL: "https://backend.example.test",
      SUPABASE_ANON_KEY: "test-public-fixture", SUPABASE_SERVICE_ROLE_KEY: "test-server-fixture" } as Record<string, string>)[key];
    globalThis.fetch = (input, init) => {
      const request = new Request(input, init);
      assert(new URL(request.url).origin === "https://backend.example.test", "Unexpected destination");
      return Promise.resolve(backend(request));
    };
    if (!handlers.has(name)) {
      Deno.serve = ((handler: Handler) => { handlers.set(name, handler); return {}; }) as typeof Deno.serve;
      if (name === "download") await import("../functions/catalog-download/index.ts");
      else if (name === "finalize") await import("../functions/catalog-finalize-upload/index.ts");
      else throw new Error("Unknown test handler");
    }
    const response = await handlers.get(name)!(new Request("https://edge.example.test/", {
      method: "POST", headers: { "Content-Type": "application/json", Authorization: "Bearer test-fixture", ...headers },
      body: JSON.stringify({ bookId }),
    }));
    await check(response);
    await response.body?.cancel().catch(() => {});
  } finally { Deno.serve = serve; Deno.env.get = getEnv; globalThis.fetch = fetch; }
}
function auth(request: Request) {
  return new URL(request.url).pathname === "/auth/v1/user"
    ? json({ id: owner, is_anonymous: false }) : null;
}
Deno.test("withdrawal committed during private object fetch prevents delivery", async () => {
  let lookups = 0;
  let fetched = false;
  await run("download", (request) => {
    const user = auth(request); if (user) return user;
    if (request.url.includes("/rest/v1/")) {
      lookups++;
      return json(fetched ? null : book); // Retraction committed while Storage request was in flight.
    }
    assert(request.url.includes("/storage/v1/"), "Unexpected backend call");
    fetched = true;
    return new Response(new Uint8Array(epub()).buffer);
  }, (response) => {
    assert(response.status === 404, "Withdrawn book delivered after Storage fetch");
    assert(lookups === 2, "Publication must be rechecked before delivery");
  });
});
Deno.test("pending upload beyond deadline cannot be finalized before cleanup", async () => {
  let storageReads = 0;
  await run("finalize", (request) => {
    const user = auth(request); if (user) return user;
    if (request.url.includes("/rest/v1/")) return json({ ...book, status: "pending", sha256: null,
      expires_at: "2000-01-01T00:00:00Z" });
    storageReads++;
    return new Response(new Uint8Array(epub()).buffer);
  }, (response) => {
    assert(response.status === 409, "Expired upload finalized despite advertised deadline");
    assert(storageReads === 0, "Expired upload must fail before expensive Storage validation");
  });
});
Deno.test("anonymous users and expired JWTs cannot reach catalogue or Storage", async () => {
  for (const anonymous of [true, false]) {
    let reads = 0;
    await run("download", (request) => {
      if (request.url.includes("/auth/v1/user")) return anonymous
        ? json({ id: owner, is_anonymous: true }) : json({ message: "invalid token" }, 401);
      reads++;
      throw new Error("Unauthorized backend operation");
    }, (response) => {
      assert(response.status === 401, "Invalid caller accepted");
      assert(reads === 0, "Caller must be verified before privileged backend use");
    });
  }
});
Deno.test("failed publication recheck refuses bytes with a generic unavailable error", async () => {
  let reads = 0;
  await run("download", (request) => {
    const user = auth(request); if (user) return user;
    if (request.url.includes("/rest/v1/")) return ++reads === 1 ? json(book)
      : json({ message: "private database details must never escape", code: "XX000" }, 500);
    return new Response(new Uint8Array(epub()).buffer);
  }, async (response) => {
    assert(response.status === 503, "Unverified response must fail closed");
    assert(!(await response.text()).includes("private database"), "Backend details exposed");
  });
});
Deno.test("still-published download returns bytes only after both authorization checks", async () => {
  let reads = 0;
  const bytes = epub();
  await run("download", (request) => {
    const user = auth(request); if (user) return user;
    if (request.url.includes("/rest/v1/")) { reads++; return json(book); }
    return new Response(new Uint8Array(bytes).buffer);
  }, async (response) => {
    assert(response.status === 200 && reads === 2, "Fresh published book must remain downloadable");
    assert((await response.arrayBuffer()).byteLength === bytes.length, "Download corrupted");
    assert(response.headers.get("Cache-Control")?.includes("no-store"), "Private download must not be cached");
  });
});
Deno.test("deadline crossing during file validation is rejected by the database boundary", async () => {
  let patches = 0;
  await run("finalize", (request) => {
    const user = auth(request); if (user) return user;
    if (request.method === "PATCH") {
      patches++;
      return json({ code: "P0001", message: "catalog_upload_expired" }, 400);
    }
    if (request.url.includes("/rest/v1/")) return json({ ...book, status: "pending", sha256: null,
      expires_at: "2099-01-01T00:00:00Z" });
    return new Response(new Uint8Array(epub()).buffer);
  }, (response) => {
    assert(response.status === 409 && patches === 1, "Late validation must not be reported as successful");
  });
});
Deno.test("live owner upload can still finalize once and remain pending", async () => {
  let patches = 0;
  await run("finalize", async (request) => {
    const user = auth(request); if (user) return user;
    if (request.method === "PATCH") {
      patches++;
      const update = await request.json();
      assert(update.expires_at === null && /^[a-f0-9]{64}$/.test(update.sha256), "Validated hash missing");
      const url = new URL(request.url);
      assert(url.searchParams.get("owner_id") === `eq.${owner}` && url.searchParams.get("status") === "eq.pending", "Owner/state scope lost");
      return json({ id: bookId });
    }
    if (request.url.includes("/rest/v1/")) return json({ ...book, status: "pending", sha256: null,
      expires_at: "2099-01-01T00:00:00Z" });
    return new Response(new Uint8Array(epub()).buffer);
  }, async (response) => {
    const result = await response.json();
    assert(response.status === 200 && result.status === "pending" && result.validated && patches === 1, "Finalization must not publish or replay");
  });
});
