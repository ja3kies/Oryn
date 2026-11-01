-- Oryn community catalogue: per-owner creation limits and abandoned-upload expiry.
-- Apply only after reviewing on a disposable Supabase project.

alter table public.community_shared_books
    drop constraint if exists community_shared_books_status_check;

alter table public.community_shared_books
    add constraint community_shared_books_status_check
    check (status in ('pending', 'published', 'rejected', 'removed', 'expired'));

alter table public.community_shared_books
    add column if not exists expires_at timestamptz default (now() + interval '4 hours'),
    add column if not exists expired_at timestamptz,
    add column if not exists storage_cleanup_at timestamptz;

alter table public.community_shared_books
    drop constraint if exists community_shared_books_publication_state_check;

alter table public.community_shared_books
    add constraint community_shared_books_publication_state_check
    check (
        (status = 'published' and published_at is not null and removed_at is null and expired_at is null)
        or (status in ('pending', 'rejected') and published_at is null and removed_at is null and expired_at is null)
        or (status = 'removed' and removed_at is not null and expired_at is null)
        or (status = 'expired' and published_at is null and removed_at is null and expired_at is not null)
    );

alter table public.community_shared_books
    drop constraint if exists community_shared_books_removed_state_check;

alter table public.community_shared_books
    add constraint community_shared_books_removed_state_check
    check ((status = 'removed') = (removed_at is not null));

alter table public.community_shared_books
    add constraint community_shared_books_expired_state_check
    check ((status = 'expired') = (expired_at is not null));

create index if not exists community_shared_books_expired_uploads_idx
    on public.community_shared_books (expires_at, id)
    where status = 'pending' and expires_at is not null;

-- Per-owner serialization closes concurrent insert races around the limits.
-- Rejected/removed/expired rows still count toward the hourly creation limit,
-- while only pending/published rows consume the active catalogue quota.
create or replace function private.enforce_community_catalog_limits()
returns trigger
language plpgsql
set search_path = ''
as $$
declare
    v_recent_creations bigint;
    v_active_books bigint;
    v_pending_books bigint;
begin
    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(new.owner_id::text, 734921)
    );

    select count(*)
      into v_recent_creations
      from public.community_shared_books b
     where b.owner_id = new.owner_id
       and b.created_at >= now() - interval '1 hour';

    if v_recent_creations >= 10 then
        raise exception using
            errcode = 'P0001',
            message = 'catalog_limit:hourly_creation';
    end if;

    select
        count(*) filter (where b.status in ('pending', 'published')),
        count(*) filter (where b.status = 'pending')
      into v_active_books, v_pending_books
      from public.community_shared_books b
     where b.owner_id = new.owner_id;

    if v_active_books >= 30 then
        raise exception using
            errcode = 'P0001',
            message = 'catalog_limit:active_books';
    end if;

    if v_pending_books >= 3 then
        raise exception using
            errcode = 'P0001',
            message = 'catalog_limit:pending_uploads';
    end if;

    return new;
end;
$$;

revoke all on function private.enforce_community_catalog_limits() from public, anon, authenticated;
grant usage on schema private to service_role;
grant execute on function private.enforce_community_catalog_limits() to service_role;

drop trigger if exists community_catalog_creation_limits on public.community_shared_books;
create trigger community_catalog_creation_limits
before insert on public.community_shared_books
for each row execute function private.enforce_community_catalog_limits();

comment on column public.community_shared_books.expires_at is
    'Pending upload deadline; cleared after successful EPUB validation.';
comment on column public.community_shared_books.expired_at is
    'Timestamp when an abandoned pending upload was expired.';
comment on column public.community_shared_books.storage_cleanup_at is
    'Timestamp when the expired upload object was successfully removed.';
