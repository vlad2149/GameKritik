-- Create a profile for every Auth signup (including pending confirmation).
-- Passwords are handled only by Supabase Auth; public.users has a sentinel.
begin;
create or replace function public.gk_sync_auth_user(p_auth_id uuid) returns void
language plpgsql security definer set search_path = '' as $$
declare a auth.users%rowtype; nick text;
begin
  select * into a from auth.users where id=p_auth_id;
  if a.id is null or a.email is null then return; end if;
  perform pg_advisory_xact_lock(173021);
  if exists(select 1 from public.users where auth_id=a.id) then return; end if;
  nick := left(coalesce(nullif(trim(a.raw_user_meta_data->>'name'),''),'Игрок'),40);
  if exists(select 1 from public.users where name=nick) then nick := left(nick,7)||'_'||replace(a.id::text,'-',''); end if;
  insert into public.users(name,email,password,auth_id) values(nick,a.email,'!supabase-auth!',a.id);
end $$;
revoke all on function public.gk_sync_auth_user(uuid) from public,anon,authenticated;
create or replace function public.gk_on_auth_signup() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  perform public.gk_sync_auth_user(new.id);
  return new;
end $$;
revoke all on function public.gk_on_auth_signup() from public,anon,authenticated;
create or replace trigger gk_auth_signup after insert on auth.users for each row execute function public.gk_on_auth_signup();
-- Backfill accounts created before this migration, without changing credentials.
select public.gk_sync_auth_user(id) from auth.users;
notify pgrst,'reload schema';
commit;

