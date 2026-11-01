-- Oryn community EPUB catalog: private-by-default schema.
-- This migration is intentionally not production-ready until the trusted
-- Edge Functions and runtime authorization tests described in the docs exist.
--
-- IMPORTANT:
-- - Keep the "private" schema out of Supabase's exposed Data API schemas.
-- - Creation/retraction/publication/downloads are server-only operations.
-- - Never put a service_role/secret key in the Android application.

create schema if not exists private;
revoke all on schema private from public, anon, authenticated;
-- Storage INSERT policies execute as the requesting authenticated role.
-- Grant schema usage only so that the policy can call the single helper below;
-- the schema must still remain absent from the Data API exposed-schema list.
grant usage on schema private to authenticated;

create table if not exists public.community_shared_books (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references auth.users(id) on delete cascade,
    title text not null check (char_length(btrim(title)) between 1 and 200),
    author text not null check (char_length(btrim(author)) between 1 and 200),
    language text not null check (language ~ '^[a-z]{2,3}(-[A-Za-z0-9]{2,8})*$'),
    status text not null default 'pending'
        check (status in ('pending', 'published', 'rejected', 'removed')),
    storage_key text generated always as (owner_id::text || '/' || id::text || '.epub') stored,
    sha256 text check (sha256 is null or sha256 ~ '^[a-f0-9]{64}$'),
    rights_declaration_version text not null
        check (char_length(btrim(rights_declaration_version)) between 1 and 32),
    consent_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    published_at timestamptz,
    removed_at timestamptz,
    constraint community_shared_books_publication_state_check
        check (
            (status = 'published' and published_at is not null and removed_at is null)
            or (status <> 'published' and published_at is null)
        ),
    constraint community_shared_books_removed_state_check
        check ((status = 'removed') = (removed_at is not null))
);

create index if not exists community_shared_books_published_search_idx
    on public.community_shared_books (published_at desc, id)
    where status = 'published';

create index if not exists community_shared_books_owner_idx
    on public.community_shared_books (owner_id, created_at desc);

alter table public.community_shared_books enable row level security;
alter table public.community_shared_books force row level security;

revoke all on table public.community_shared_books from public, anon, authenticated;
grant select (id, title, author, language, status, created_at, published_at)
    on table public.community_shared_books to authenticated;

drop policy if exists "authenticated read published or own community books"
    on public.community_shared_books;
create policy "authenticated read published or own community books"
    on public.community_shared_books
    for select
    to authenticated
    using (status = 'published' or owner_id = (select auth.uid()));

-- No SECURITY DEFINER function is placed in the exposed public schema.
-- Edge Functions must validate the caller with auth.getUser(accessToken),
-- then perform create/retract/moderation through a server-only DB client.
-- Never trust a user_id supplied in the request body.

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
    'community-epubs',
    'community-epubs',
    false,
    8388608,
    array['application/epub+zip', 'application/zip']
)
on conflict (id) do update
set public = false,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

-- This helper is only used by the Storage INSERT policy. It deliberately
-- exposes no metadata and can only answer whether this exact pending object
-- belongs to the authenticated caller.
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
           and b.storage_key = p_object_name
    );
$$;

revoke all on function private.can_upload_community_epub(text) from public, anon;
grant execute on function private.can_upload_community_epub(text) to authenticated;

drop policy if exists "owner uploads only pending community EPUB"
    on storage.objects;
create policy "owner uploads only pending community EPUB"
    on storage.objects
    for insert
    to authenticated
    with check (
        bucket_id = 'community-epubs'
        and private.can_upload_community_epub(name)
    );

comment on table public.community_shared_books is
    'Opt-in community EPUB metadata. Publication is server-controlled; file objects are private.';
comment on schema private is
    'Internal Oryn catalogue helpers. Never add this schema to Supabase Data API exposed schemas.';
