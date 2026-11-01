-- Static privilege/policy assertions for the Oryn community catalog.
-- Run after the migration on a disposable Supabase development database:
--   psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f supabase/tests/community_catalog_security.sql
-- This is not a substitute for two-user API/Storage integration tests.

begin;

do $checks$
declare
    v_helper regprocedure := to_regprocedure('private.can_upload_community_epub(text)');
begin
    if v_helper is null then
        raise exception 'Missing private Storage authorization helper';
    end if;

    if exists (
        select 1
          from pg_catalog.pg_proc p
          join pg_catalog.pg_namespace n on n.oid = p.pronamespace
         where n.nspname = 'public'
           and p.prosecdef
           and p.proname like '%community%'
    ) then
        raise exception 'SECURITY DEFINER community function found in exposed public schema';
    end if;

    if not has_schema_privilege('authenticated', 'private', 'USAGE') then
        raise exception 'authenticated must have schema USAGE for the Storage policy helper';
    end if;

    if has_schema_privilege('anon', 'private', 'USAGE') then
        raise exception 'anon must not have USAGE on private schema';
    end if;

    if has_function_privilege('anon', v_helper, 'EXECUTE') then
        raise exception 'anon must not execute the private Storage helper';
    end if;

    if not has_function_privilege('authenticated', v_helper, 'EXECUTE') then
        raise exception 'authenticated must execute the private Storage helper for upload policy';
    end if;

    if not exists (
        select 1
          from pg_catalog.pg_proc p
         where p.oid = v_helper
           and p.prosecdef
           and coalesce(array_to_string(p.proconfig, ','), '') like '%search_path=%'
    ) then
        raise exception 'Private helper must be SECURITY DEFINER with a pinned search_path';
    end if;

    if not exists (
        select 1
          from pg_catalog.pg_class c
          join pg_catalog.pg_namespace n on n.oid = c.relnamespace
         where n.nspname = 'public'
           and c.relname = 'community_shared_books'
           and c.relrowsecurity
           and c.relforcerowsecurity
    ) then
        raise exception 'Catalogue table must have RLS enabled and forced';
    end if;

    if has_table_privilege('anon', 'public.community_shared_books', 'SELECT')
       or has_table_privilege('anon', 'public.community_shared_books', 'INSERT')
       or has_table_privilege('anon', 'public.community_shared_books', 'UPDATE')
       or has_table_privilege('anon', 'public.community_shared_books', 'DELETE') then
        raise exception 'anon has an unexpected table-level privilege';
    end if;

    if has_table_privilege('authenticated', 'public.community_shared_books', 'INSERT')
       or has_table_privilege('authenticated', 'public.community_shared_books', 'UPDATE')
       or has_table_privilege('authenticated', 'public.community_shared_books', 'DELETE') then
        raise exception 'authenticated has an unexpected write privilege on catalogue table';
    end if;

    if not has_column_privilege('authenticated', 'public.community_shared_books', 'title', 'SELECT')
       or has_column_privilege('authenticated', 'public.community_shared_books', 'owner_id', 'SELECT')
       or has_column_privilege('authenticated', 'public.community_shared_books', 'storage_key', 'SELECT') then
        raise exception 'Catalogue column grants do not match the intended metadata-only read model';
    end if;

    if not exists (
        select 1
          from pg_catalog.pg_policies
         where schemaname = 'storage'
           and tablename = 'objects'
           and policyname = 'owner uploads only pending community EPUB'
           and cmd = 'INSERT'
           and roles @> array['authenticated']::name[]
    ) then
        raise exception 'Missing authenticated-only pending EPUB upload policy';
    end if;

    if exists (
        select 1
          from pg_catalog.pg_policies
         where schemaname = 'storage'
           and tablename = 'objects'
           and policyname <> 'owner uploads only pending community EPUB'
           and cmd in ('SELECT', 'UPDATE', 'DELETE', 'ALL')
           and qual like '%community-epubs%'
    ) then
        raise exception 'Unexpected client read/update/delete policy references community bucket';
    end if;
end;
$checks$;


do $quota_checks$
declare
    v_limit_fn regprocedure := to_regprocedure('private.enforce_community_catalog_limits()');
begin
    if v_limit_fn is null then
        raise exception 'Missing per-owner catalogue quota trigger function';
    end if;

    if not exists (
        select 1
          from pg_catalog.pg_trigger t
          join pg_catalog.pg_class c on c.oid = t.tgrelid
          join pg_catalog.pg_namespace n on n.oid = c.relnamespace
         where n.nspname = 'public'
           and c.relname = 'community_shared_books'
           and t.tgname = 'community_catalog_creation_limits'
           and not t.tgisinternal
    ) then
        raise exception 'Missing catalogue creation quota trigger';
    end if;

    if has_function_privilege('anon', v_limit_fn, 'EXECUTE')
       or has_function_privilege('authenticated', v_limit_fn, 'EXECUTE') then
        raise exception 'Client roles must not execute the quota trigger function directly';
    end if;

    if not has_function_privilege('service_role', v_limit_fn, 'EXECUTE') then
        raise exception 'service_role must execute the quota trigger function';
    end if;

    if not exists (
        select 1
          from information_schema.columns
         where table_schema = 'public'
           and table_name = 'community_shared_books'
           and column_name = 'expires_at'
    ) or not exists (
        select 1
          from information_schema.columns
         where table_schema = 'public'
           and table_name = 'community_shared_books'
           and column_name = 'storage_cleanup_at'
    ) then
        raise exception 'Missing abandoned-upload lifecycle columns';
    end if;

    if not exists (
        select 1
          from pg_catalog.pg_constraint con
          join pg_catalog.pg_class c on c.oid = con.conrelid
          join pg_catalog.pg_namespace n on n.oid = c.relnamespace
         where n.nspname = 'public'
           and c.relname = 'community_shared_books'
           and con.conname = 'community_shared_books_status_check'
           and pg_catalog.pg_get_constraintdef(con.oid) like '%expired%'
    ) then
        raise exception 'Catalogue status constraint must include expired tombstones';
    end if;
end;
$quota_checks$;

rollback;

select 'Oryn community catalog static security assertions passed' as result;
