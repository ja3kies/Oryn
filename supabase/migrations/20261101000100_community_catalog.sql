-- Oryn community EPUB catalog: initial private-by-default schema.
-- Apply only to a dedicated Supabase project after reviewing the security plan.
-- No public bucket, anonymous read, or client-controlled publication status.

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

revoke all on table public.community_shared_books from anon, authenticated;
grant select (id, title, author, language, status, created_at, published_at)
    on table public.community_shared_books to authenticated;

drop policy if exists "authenticated read published or own community books"
    on public.community_shared_books;
create policy "authenticated read published or own community books"
    on public.community_shared_books
    for select
    to authenticated
    using (status = 'published' or owner_id = (select auth.uid()));

-- The client cannot insert/update/delete rows directly. Creation and withdrawal
-- go through narrowly scoped RPCs so owner/status/consent cannot be forged.
create or replace function public.create_community_shared_book(
    p_title text,
    p_author text,
    p_language text,
    p_rights_declaration_version text
)
returns table (book_id uuid, object_path text)
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_owner uuid := auth.uid();
    v_book_id uuid;
    v_path text;
begin
    if v_owner is null then
        raise exception 'Authentication required' using errcode = '28000';
    end if;

    if p_title is null or char_length(btrim(p_title)) not between 1 and 200
       or p_author is null or char_length(btrim(p_author)) not between 1 and 200
       or p_language is null or p_language !~ '^[a-z]{2,3}(-[A-Za-z0-9]{2,8})*$'
       or p_rights_declaration_version is null
       or char_length(btrim(p_rights_declaration_version)) not between 1 and 32 then
        raise exception 'Invalid book metadata or consent declaration'
            using errcode = '22023';
    end if;

    -- The client must explicitly invoke this function after the consent screen.
    insert into public.community_shared_books (
        owner_id, title, author, language, rights_declaration_version, consent_at
    )
    values (
        v_owner, btrim(p_title), btrim(p_author), p_language,
        btrim(p_rights_declaration_version), pg_catalog.now()
    )
    returning id, storage_key into v_book_id, v_path;

    return query select v_book_id, v_path;
end;
$$;

revoke all on function public.create_community_shared_book(text, text, text, text)
    from public, anon;
grant execute on function public.create_community_shared_book(text, text, text, text)
    to authenticated;

create or replace function public.retract_community_shared_book(p_book_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_owner uuid := auth.uid();
begin
    if v_owner is null then
        raise exception 'Authentication required' using errcode = '28000';
    end if;

    update public.community_shared_books
       set status = 'removed',
           removed_at = pg_catalog.now(),
           published_at = null
     where id = p_book_id
       and owner_id = v_owner
       and status in ('pending', 'published');

    if not found then
        raise exception 'Book not found or already withdrawn'
            using errcode = 'P0002';
    end if;
end;
$$;

revoke all on function public.retract_community_shared_book(uuid) from public, anon;
grant execute on function public.retract_community_shared_book(uuid) to authenticated;

-- Storage stays private. A user can upload only to the exact object path
-- generated for one of their pending catalogue records. No read/delete policy
-- is granted to mobile clients; a trusted server function handles downloads
-- and physical cleanup after checking publication/ownership.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
    'community-epubs',
    'community-epubs',
    false,
    52428800,
    array['application/epub+zip', 'application/zip']
)
on conflict (id) do update
set public = false,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

create or replace function public.can_upload_community_epub(p_object_name text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
          from public.community_shared_books b
         where b.owner_id = auth.uid()
           and b.status = 'pending'
           and b.storage_key = p_object_name
    );
$$;

revoke all on function public.can_upload_community_epub(text) from public, anon;
grant execute on function public.can_upload_community_epub(text) to authenticated;

drop policy if exists "owner uploads only pending community EPUB"
    on storage.objects;
create policy "owner uploads only pending community EPUB"
    on storage.objects
    for insert
    to authenticated
    with check (
        bucket_id = 'community-epubs'
        and public.can_upload_community_epub(name)
    );

comment on table public.community_shared_books is
    'Opt-in community EPUB metadata. Publication status is server-controlled; file objects are private.';
