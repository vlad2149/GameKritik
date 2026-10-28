-- GameKritik: connection to the existing eight tables.
-- Run once in Supabase SQL Editor after reviewing access changes.
-- No existing rows are deleted. RLS stays enabled.
begin;

alter table public.users add column if not exists auth_id uuid unique references auth.users(id);
alter table public.users enable row level security;
alter table public.favorites enable row level security;
-- users (including email/password) and favorites remain inaccessible directly.
revoke all on public.users, public.favorites from anon, authenticated;

do $$
declare t text;
begin
  foreach t in array array['games','guides','mods','screenshots','trailers'] loop
    execute format('alter table public.%I enable row level security',t);
    execute format('grant select on public.%I to anon, authenticated',t);
    if not exists (select 1 from pg_policies where schemaname='public' and tablename=t and policyname='gk_catalog_read') then
      execute format('create policy gk_catalog_read on public.%I for select to anon, authenticated using (true)',t);
    end if;
  end loop;
end $$;

-- Obtain or create only the caller's profile. Never accept an owner ID from a client.
create or replace function public.gk_profile() returns jsonb
language plpgsql security definer set search_path = '' as $$
declare uid uuid := auth.uid(); account auth.users%rowtype; p public.users%rowtype; nick text;
begin
  if uid is null then raise exception 'Authentication required'; end if;
  perform pg_advisory_xact_lock(hashtextextended(uid::text,0));
  select * into p from public.users where auth_id=uid;
  if not found then
    select * into account from auth.users where id=uid;
    if account.id is null or account.email_confirmed_at is null then raise exception 'Email not confirmed'; end if;
    nick := left(coalesce(nullif(trim(account.raw_user_meta_data->>'name'),''),'Игрок'),40);
    if exists(select 1 from public.users where name=nick) then nick := left(nick,28)||'_'||left(uid::text,8); end if;
    insert into public.users(name,email,password,auth_id)
      values(nick,account.email,'!supabase-auth!',uid) returning * into p;
  end if;
  return jsonb_build_object('user_id',p.user_id,'name',p.name,'email',p.email);
end $$;

create or replace function public.gk_favorites() returns jsonb
language plpgsql security definer set search_path = '' as $$
declare owner_id integer;
begin
  owner_id := (public.gk_profile()->>'user_id')::integer;
  return coalesce((select jsonb_agg(jsonb_build_object('game_id',f.game_id) order by f.added_date desc) from public.favorites f where f.user_id=owner_id),'[]'::jsonb);
end $$;

create or replace function public.gk_set_favorite(p_game_id integer, p_add boolean) returns void
language plpgsql security definer set search_path = '' as $$
declare owner_id integer;
begin
  owner_id := (public.gk_profile()->>'user_id')::integer;
  if p_add then
    insert into public.favorites(user_id,game_id) values(owner_id,p_game_id) on conflict(user_id,game_id) do nothing;
  else
    delete from public.favorites where user_id=owner_id and game_id=p_game_id;
  end if;
end $$;

-- Public reviews expose a nickname only, never an email or password.
create or replace function public.gk_reviews(p_game_id integer) returns jsonb
language sql stable security definer set search_path = '' as $$
  select coalesce(jsonb_agg(jsonb_build_object('review_id',r.review_id,'name',u.name,'text',r.text,'rating',r.rating,'date',r.date) order by r.date desc),'[]'::jsonb)
  from public.reviews r join public.users u on u.user_id=r.user_id where r.game_id=p_game_id;
$$;

create or replace function public.gk_write_review(p_game_id integer,p_text text,p_rating integer) returns void
language plpgsql security definer set search_path = '' as $$
declare owner_id integer; existing_id integer;
begin
  owner_id := (public.gk_profile()->>'user_id')::integer;
  if p_rating is null or p_rating not between 1 and 10 then raise exception 'Оценка должна быть от 1 до 10'; end if;
  if p_text is null or length(trim(p_text)) not between 10 and 5000 then raise exception 'Отзыв должен содержать от 10 до 5000 символов'; end if;
  -- Serialize updates per game to keep its average rating consistent.
  perform 1 from public.games where game_id=p_game_id for update;
  if not found then raise exception 'Игра не найдена'; end if;
  select review_id into existing_id from public.reviews where game_id=p_game_id and user_id=owner_id order by date desc limit 1;
  if existing_id is null then
    insert into public.reviews(game_id,user_id,text,rating) values(p_game_id,owner_id,trim(p_text),p_rating);
  else
    update public.reviews set text=trim(p_text),rating=p_rating,date=now() where review_id=existing_id;
  end if;
  update public.games set rating=(select avg(rating) from public.reviews where game_id=p_game_id) where game_id=p_game_id;
end $$;

revoke all on function public.gk_profile() from public,anon;
revoke all on function public.gk_favorites() from public,anon;
revoke all on function public.gk_set_favorite(integer,boolean) from public,anon;
revoke all on function public.gk_write_review(integer,text,integer) from public,anon;
revoke all on function public.gk_reviews(integer) from public;
grant execute on function public.gk_profile(),public.gk_favorites(),public.gk_set_favorite(integer,boolean),public.gk_write_review(integer,text,integer) to authenticated;
grant execute on function public.gk_reviews(integer) to anon,authenticated;
notify pgrst,'reload schema';
commit;
