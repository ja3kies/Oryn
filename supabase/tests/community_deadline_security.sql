-- Transactional regression, prepared but NOT executed without isolated Supabase.
-- Run after all migrations with a privileged isolated test connection, never production.
begin;
insert into auth.users (id, is_anonymous) values
    ('70000000-0000-4000-8000-000000000001', false),
    ('70000000-0000-4000-8000-000000000002', false);
insert into public.community_shared_books
    (id, owner_id, title, author, language, rights_declaration_version, expires_at)
values ('71000000-0000-4000-8000-000000000001', '70000000-0000-4000-8000-000000000001',
    'Deadline test only', 'Oryn tests', 'en', 'oryn-epub-rights-v1', now() + interval '1 hour');
select set_config('request.jwt.claim.sub', '70000000-0000-4000-8000-000000000001', true);
set local role authenticated;
do $$ begin
    if not private.can_upload_community_epub('70000000-0000-4000-8000-000000000001/71000000-0000-4000-8000-000000000001.epub') then
        raise exception 'Live owner upload refused';
    end if;
end $$;
select set_config('request.jwt.claim.sub', '70000000-0000-4000-8000-000000000002', true);
do $$ begin
    if private.can_upload_community_epub('70000000-0000-4000-8000-000000000001/71000000-0000-4000-8000-000000000001.epub') then
        raise exception 'Other owner upload allowed';
    end if;
end $$;
reset role;
update public.community_shared_books set expires_at = now() - interval '1 second'
where id = '71000000-0000-4000-8000-000000000001';
select set_config('request.jwt.claim.sub', '70000000-0000-4000-8000-000000000001', true);
set local role authenticated;
do $$ begin
    if private.can_upload_community_epub('70000000-0000-4000-8000-000000000001/71000000-0000-4000-8000-000000000001.epub') then
        raise exception 'Expired ticket upload allowed before cleanup';
    end if;
end $$;
reset role;
do $$ begin
    begin
        update public.community_shared_books set sha256 = repeat('a', 64), expires_at = null
        where id = '71000000-0000-4000-8000-000000000001';
        raise exception 'Late validation bypassed database deadline';
    exception when sqlstate 'P0001' then
        if sqlerrm <> 'catalog_upload_expired' then raise; end if;
    end;
end $$;
update public.community_shared_books set expires_at = now() + interval '1 hour'
where id = '71000000-0000-4000-8000-000000000001';
update public.community_shared_books set sha256 = repeat('a', 64), expires_at = null
where id = '71000000-0000-4000-8000-000000000001';
set local role authenticated;
do $$ begin
    if private.can_upload_community_epub('70000000-0000-4000-8000-000000000001/71000000-0000-4000-8000-000000000001.epub') then
        raise exception 'Validated ticket reused for a new upload';
    end if;
end $$;
reset role;
rollback;
select 'Deadline and owner isolation regressions passed' as result;
