-- Tighten the existing Storage boundary; never enable public reads or client updates.
-- Prepared locally only. Apply and validate exclusively on an isolated Supabase instance first.
create or replace function private.can_upload_community_epub(p_object_name text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
          from public.community_shared_books b
         where b.owner_id = (select auth.uid())
           and b.status = 'pending'
           and b.sha256 is null
           and b.expires_at > pg_catalog.statement_timestamp()
           and b.storage_key = p_object_name
    );
$$;

-- Also check at the authoritative DB update, after potentially slow file validation.
-- No client permission is granted and no SECURITY DEFINER function is added.
create or replace function private.enforce_community_validation_deadline()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    if old.sha256 is null and new.sha256 is not null
       and (old.expires_at is null or old.expires_at <= pg_catalog.clock_timestamp()) then
        raise exception using errcode = 'P0001', message = 'catalog_upload_expired';
    end if;
    return new;
end;
$$;
revoke all on function private.enforce_community_validation_deadline() from public, anon, authenticated;
grant execute on function private.enforce_community_validation_deadline() to service_role;

create trigger community_validation_deadline
before update of sha256 on public.community_shared_books
for each row execute function private.enforce_community_validation_deadline();
