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

rollback;

select 'Oryn community catalog static security assertions passed' as result;
