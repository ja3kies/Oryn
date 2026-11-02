import { localCatalogTestSettings } from "./local_test_settings.ts";
function require(value: unknown) { if (!value) throw new Error("Local safety regression"); }
const fixture: Record<string, string> = {
  ORYN_CATALOG_TEST_LOCAL_VERIFIED: "true", SUPABASE_URL: "http://127.0.0.1:54321",
  FUNCTIONS_URL: "http://127.0.0.1:54321/functions/v1", SUPABASE_ANON_KEY: "test-public-key",
  SUPABASE_SERVICE_ROLE_KEY: "test-server-only-fixture", CATALOG_CLEANUP_SECRET: "test-only-cleanup-value-not-real!!",
};
Deno.test("no opt-in never accesses credentials or destinations", () => {
  require(localCatalogTestSettings((key) => {
    if (key !== "ORYN_CATALOG_TEST_LOCAL_VERIFIED") throw new Error("Unexpected credential read");
    return undefined;
  }) === null);
});
Deno.test("accepts complete explicitly verified loopback configuration", () => {
  require(localCatalogTestSettings((key) => fixture[key])?.url === fixture.SUPABASE_URL);
});
Deno.test("production, mixed origins and polluted URLs fail before credential access", () => {
  const attacks: Record<string, string>[] = [
    { SUPABASE_URL: "https://project.supabase.co" },
    { SUPABASE_URL: "http://127.0.0.1.attacker.test:54321" },
    { SUPABASE_URL: "http://user@127.0.0.1:54321" },
    { SUPABASE_URL: "http://127.0.0.1:54321/?redirect=other" },
    { FUNCTIONS_URL: "https://project.supabase.co/functions/v1" },
    { FUNCTIONS_URL: "http://127.0.0.1:54322/functions/v1" },
    { FUNCTIONS_URL: "http://127.0.0.1:54321/other" },
  ];
  for (const attack of attacks) {
    let refused = false;
    try {
      localCatalogTestSettings((key) => {
        if (key.includes("KEY") || key.includes("SECRET")) throw new Error("Credentials accessed before URL rejection");
        return ({ ...fixture, ...attack })[key];
      });
    } catch (error) {
      require(!(error instanceof Error) || !error.message.includes("Credentials accessed"));
      refused = true;
    }
    require(refused);
  }
});
Deno.test("explicit incomplete local configuration fails rather than skips", () => {
  let failed = false;
  try { localCatalogTestSettings((key) => key === "SUPABASE_SERVICE_ROLE_KEY" ? "" : fixture[key]); }
  catch { failed = true; }
  require(failed);
});
