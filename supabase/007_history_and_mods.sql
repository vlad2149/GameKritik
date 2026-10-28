-- GameKritik: private viewing history and useful mod-source pages.
begin;
create table if not exists public.game_history (
 user_id integer not null references public.users(user_id) on delete cascade,
 game_id integer not null references public.games(game_id) on delete cascade,
 viewed_at timestamptz not null default now(),
 primary key(user_id,game_id)
);
create index if not exists game_history_owner_recent on public.game_history(user_id,viewed_at desc);
alter table public.game_history enable row level security;
revoke all on public.game_history from anon,authenticated;
create or replace function public.gk_history() returns jsonb language sql stable security definer set search_path = '' as $$
select coalesce(jsonb_agg(jsonb_build_object('game_id',h.game_id) order by h.viewed_at desc),'[]'::jsonb)
from (select game_id,viewed_at from public.game_history where user_id=(public.gk_profile()->>'user_id')::integer order by viewed_at desc limit 30) h;
$$;
create or replace function public.gk_record_view(p_game_id integer) returns void language plpgsql security definer set search_path = '' as $$
declare owner_id integer; begin
owner_id := (public.gk_profile()->>'user_id')::integer;
if not exists(select 1 from public.games where game_id=p_game_id) then raise exception 'Игра не найдена'; end if;
insert into public.game_history(user_id,game_id,viewed_at) values(owner_id,p_game_id,now())
on conflict(user_id,game_id) do update set viewed_at=excluded.viewed_at;
delete from public.game_history where user_id=owner_id and game_id in (
 select game_id from public.game_history where user_id=owner_id order by viewed_at desc offset 30);
end $$;
revoke all on function public.gk_history() from public,anon;
revoke all on function public.gk_record_view(integer) from public,anon;
grant execute on function public.gk_history(),public.gk_record_view(integer) to authenticated;
do $mods$ declare g record; editor_id integer; has_owner boolean; destination text; heading text; body text; author_name text; q text; begin
select user_id into editor_id from public.users where email='editorial@gamekritik.invalid' limit 1;
select exists(select 1 from information_schema.columns where table_schema='public' and table_name='mods' and column_name='user_id') into has_owner;
for g in select game_id,steam_app_id,title,release_status from public.games where steam_app_id=any(array[292030,1245620,1086940,1091500,1174180,271590,489830,72850,377160,22380,620,220,730,550,275850,289070,1551360,391220,750920,203160,412020,286690,287390,782330,379720,883710,2050650,1196590,418370,2124490,814380,374320,570940,1888930,1593500,2322010,1151640,2420110,1817070,2651280,2215430,1659040,1172380,1774580,990080,1778820,553850,1903340,1771300,3280350,3017860,2947440,1285190,2246340,1941540,2001120,3764200,3357650,3321460,3768760,2769570,3558670,2590240,4354570,1237320,584400,2513280,1794960,2022670,287700,311340,2131630,2131640,2131650,2417610,339340,304240,952060,21690,221040,2161700,1113000,1687950,1382330,2963950]::integer[]) loop
if g.release_status='upcoming' then
 heading := 'Моды после релиза'; destination := 'https://store.steampowered.com/app/'||g.steam_app_id||'/';
 body := 'Игра ещё не вышла. Проверенных модификаций пока нет. После релиза здесь появятся ссылки на страницы сообщества. Следите за новостями на официальной странице.'; author_name := 'Официальная страница Steam';
else
 heading := 'Моды и инструкции сообщества'; destination := 'https://steamcommunity.com/app/'||g.steam_app_id||'/guides/?searchText=mod';
 body := 'Откройте подборку руководств сообщества по модификациям именно этой игры. Перед установкой проверьте совместимость с вашей версией и сделайте резервную копию сохранений. Файлы скачиваются на странице автора, а не в GameKritik.'; author_name := 'Сообщество Steam';
end if;
if has_owner then q := 'insert into public.mods(user_id,game_id,title,description,author,download_url) select $1,$2,$3,$4,$5,$6 where not exists(select 1 from public.mods where game_id=$2 and title=$3)';
else q := 'insert into public.mods(game_id,title,description,author,download_url) select $2,$3,$4,$5,$6 where not exists(select 1 from public.mods where game_id=$2 and title=$3)'; end if;
execute q using editor_id,g.game_id,heading,body,author_name,destination;
end loop;
for g in select gg.game_id,x.mod_url from (values
(292030,'https://www.nexusmods.com/games/witcher3/mods'),
(377160,'https://www.nexusmods.com/games/fallout4/mods'),
(489830,'https://www.nexusmods.com/games/skyrimspecialedition/mods'),
(883710,'https://www.nexusmods.com/games/residentevil22019/mods'),
(1086940,'https://www.nexusmods.com/games/baldursgate3/mods'),
(1091500,'https://www.nexusmods.com/games/cyberpunk2077/mods'),
(1687950,'https://www.nexusmods.com/games/persona5royal/mods'),
(2050650,'https://www.nexusmods.com/games/residentevil42023/mods')
) as x(steam_app_id,mod_url) join public.games gg on gg.steam_app_id=x.steam_app_id loop
heading := 'Каталог модов Nexus Mods'; body := 'Каталог модификаций для этой игры на Nexus Mods. Откройте страницу, проверьте версию игры, требования мода, дату обновления и инструкцию автора. Скачивание и установка выполняются на сайте источника.'; author_name := 'Nexus Mods';
execute q using editor_id,g.game_id,heading,body,author_name,g.mod_url;
end loop;
end $mods$;
notify pgrst,'reload schema';
commit;
select (select count(*) from public.game_history) history_entries,(select count(*) from public.mods) mod_links;
