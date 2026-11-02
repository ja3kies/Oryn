/** Destructive integration fixtures are allowed only on explicit, loopback-only local instances.
 * Validate destinations before accessing any credential; a project name alone proves no isolation.
 */
export function localCatalogTestSettings(get: (key: string) => string | undefined) {
  if (get("ORYN_CATALOG_TEST_LOCAL_VERIFIED") !== "true") return null;
  const origin = new URL(get("SUPABASE_URL") ?? "");
  const functions = new URL(get("FUNCTIONS_URL") ?? "");
  if (!["http:", "https:"].includes(origin.protocol) ||
      !["127.0.0.1", "localhost", "[::1]"].includes(origin.hostname) ||
      origin.username || origin.password || origin.pathname !== "/" || origin.search || origin.hash ||
      functions.origin !== origin.origin || functions.pathname !== "/functions/v1" ||
      functions.username || functions.password || functions.search || functions.hash) {
    throw new Error("Explicit loopback Supabase and same-origin Functions URLs required");
  }
  const anonKey = get("SUPABASE_ANON_KEY") ?? "";
  const serviceKey = get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
  const cleanupSecret = get("CATALOG_CLEANUP_SECRET") ?? "";
  if (!anonKey || !serviceKey || cleanupSecret.length < 32) {
    throw new Error("Complete local test credentials required; values must remain private");
  }
  return { url: origin.origin, functionsUrl: functions.href, anonKey, serviceKey, cleanupSecret };
}
