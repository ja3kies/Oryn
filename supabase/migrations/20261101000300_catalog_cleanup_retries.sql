-- Retry scheduling for expired private EPUB objects.
-- Failed objects are deferred so they cannot monopolize every cleanup batch.

alter table public.community_shared_books
    add column if not exists storage_cleanup_attempts integer not null default 0,
    add column if not exists storage_cleanup_retry_at timestamptz;

alter table public.community_shared_books
    drop constraint if exists community_shared_books_cleanup_attempts_check;

alter table public.community_shared_books
    add constraint community_shared_books_cleanup_attempts_check
    check (storage_cleanup_attempts >= 0);

create index if not exists community_shared_books_cleanup_retry_idx
    on public.community_shared_books (
        storage_cleanup_retry_at asc nulls first,
        expired_at asc,
        id
    )
    where status = 'expired' and storage_cleanup_at is null;

comment on column public.community_shared_books.storage_cleanup_attempts is
    'Number of failed attempts to delete an expired private EPUB object.';
comment on column public.community_shared_books.storage_cleanup_retry_at is
    'Earliest time an expired object with a failed cleanup attempt may be retried.';
