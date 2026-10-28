const fs = require('node:fs');
const path = require('node:path');
const dir = __dirname;
const ids = JSON.parse(fs.readFileSync(path.join(dir,'curated-keep-ids.json'),'utf8'));
const nexus = {
  292030:'witcher3',1086940:'baldursgate3',1091500:'cyberpunk2077',
  489830:'skyrimspecialedition',377160:'fallout4',883710:'residentevil22019',
  2050650:'residentevil42023',1687950:'persona5royal'
};
const links = Object.entries(nexus).map(([id,slug])=>`(${id},'https://www.nexusmods.com/games/${slug}/mods')`).join(',\n');
const featureSql = `-- GameKritik: private viewing history and useful mod-source pages.\n`+
`begin;\n`+
`create table if not exists public.game_history (\n`+
` user_id integer not null references public.users(user_id) on delete cascade,\n`+
` game_id integer not null references public.games(game_id) on delete cascade,\n`+
` viewed_at timestamptz not null default now(),\n`+
` primary key(user_id,game_id)\n);\n`+
`create index if not exists game_history_owner_recent on public.game_history(user_id,viewed_at desc);\n`+
`alter table public.game_history enable row level security;\n`+
`revoke all on public.game_history from anon,authenticated;\n`+
`create or replace function public.gk_history() returns jsonb language sql stable security definer set search_path = '' as $$\n`+
`select coalesce(jsonb_agg(jsonb_build_object('game_id',h.game_id) order by h.viewed_at desc),'[]'::jsonb)\n`+
`from (select game_id,viewed_at from public.game_history where user_id=(public.gk_profile()->>'user_id')::integer order by viewed_at desc limit 30) h;\n$$;\n`+
`create or replace function public.gk_record_view(p_game_id integer) returns void language plpgsql security definer set search_path = '' as $$\n`+
`declare owner_id integer; begin\n`+
`owner_id := (public.gk_profile()->>'user_id')::integer;\n`+
`if not exists(select 1 from public.games where game_id=p_game_id) then raise exception 'Игра не найдена'; end if;\n`+
`insert into public.game_history(user_id,game_id,viewed_at) values(owner_id,p_game_id,now())\n`+
`on conflict(user_id,game_id) do update set viewed_at=excluded.viewed_at;\n`+
`delete from public.game_history where user_id=owner_id and game_id in (\n`+
` select game_id from public.game_history where user_id=owner_id order by viewed_at desc offset 30);\n`+
`end $$;\n`+
`revoke all on function public.gk_history() from public,anon;\n`+
`revoke all on function public.gk_record_view(integer) from public,anon;\n`+
`grant execute on function public.gk_history(),public.gk_record_view(integer) to authenticated;\n`+
`do $mods$ declare g record; editor_id integer; has_owner boolean; destination text; heading text; body text; author_name text; q text; begin\n`+
`select user_id into editor_id from public.users where email='editorial@gamekritik.invalid' limit 1;\n`+
`select exists(select 1 from information_schema.columns where table_schema='public' and table_name='mods' and column_name='user_id') into has_owner;\n`+
`for g in select game_id,steam_app_id,title,release_status from public.games where steam_app_id=any(array[${ids.join(',')}]::integer[]) loop\n`+
`if g.release_status='upcoming' then\n`+
` heading := 'Моды после релиза'; destination := 'https://store.steampowered.com/app/'||g.steam_app_id||'/';\n`+
` body := 'Игра ещё не вышла. Проверенных модификаций пока нет. После релиза здесь появятся ссылки на страницы сообщества. Следите за новостями на официальной странице.'; author_name := 'Официальная страница Steam';\n`+
`else\n`+
` heading := 'Моды и инструкции сообщества'; destination := 'https://steamcommunity.com/app/'||g.steam_app_id||'/guides/?searchText=mod';\n`+
` body := 'Откройте подборку руководств сообщества по модификациям именно этой игры. Перед установкой проверьте совместимость с вашей версией и сделайте резервную копию сохранений. Файлы скачиваются на странице автора, а не в GameKritik.'; author_name := 'Сообщество Steam';\n`+
`end if;\n`+
`if has_owner then q := 'insert into public.mods(user_id,game_id,title,description,author,download_url) select $1,$2,$3,$4,$5,$6 where not exists(select 1 from public.mods where game_id=$2 and title=$3)';\n`+
`else q := 'insert into public.mods(game_id,title,description,author,download_url) select $2,$3,$4,$5,$6 where not exists(select 1 from public.mods where game_id=$2 and title=$3)'; end if;\n`+
`execute q using editor_id,g.game_id,heading,body,author_name,destination;\n`+
`end loop;\n`+
`for g in select gg.game_id,x.mod_url from (values\n${links}\n) as x(steam_app_id,mod_url) join public.games gg on gg.steam_app_id=x.steam_app_id loop\n`+
`heading := 'Каталог модов Nexus Mods'; body := 'Каталог модификаций для этой игры на Nexus Mods. Откройте страницу, проверьте версию игры, требования мода, дату обновления и инструкцию автора. Скачивание и установка выполняются на сайте источника.'; author_name := 'Nexus Mods';\n`+
`execute q using editor_id,g.game_id,heading,body,author_name,g.mod_url;\n`+
`end loop;\n`+
`end $mods$;\n`+
`notify pgrst,'reload schema';\ncommit;\n`+
`select (select count(*) from public.game_history) history_entries,(select count(*) from public.mods) mod_links;\n`;
fs.writeFileSync(path.join(dir,'007_history_and_mods.sql'),featureSql);

const cleanupSql = `-- Review before running: removes only unengaged games outside curated list.\n`+
`-- Existing favorites, reviews, viewing history, user guides, and mod rows protect a game from removal.\n`+
`begin;\n`+
`create temporary table gk_retired_games on commit drop as\n`+
`select g.game_id,g.title from public.games g\n`+
`where g.steam_app_id is not null and g.steam_app_id <> all(array[${ids.join(',')}]::integer[])\n`+
`and not exists(select 1 from public.favorites f where f.game_id=g.game_id)\n`+
`and not exists(select 1 from public.reviews r where r.game_id=g.game_id)\n`+
`and not exists(select 1 from public.mods m where m.game_id=g.game_id)\n`+
`and not exists(select 1 from public.game_history h where h.game_id=g.game_id)\n`+
`and not exists(select 1 from public.guides d where d.game_id=g.game_id and d.user_id <> (select user_id from public.users where email='editorial@gamekritik.invalid' limit 1));\n`+
`delete from public.guides where game_id in (select game_id from gk_retired_games);\n`+
`delete from public.screenshots where game_id in (select game_id from gk_retired_games);\n`+
`delete from public.trailers where game_id in (select game_id from gk_retired_games);\n`+
`delete from public.games where game_id in (select game_id from gk_retired_games);\n`+
`commit;\n`+
`select count(*) remaining_games from public.games;\n`;
fs.writeFileSync(path.join(dir,'008_remove_excess_games.sql'),cleanupSql);
console.log(`Features SQL ${featureSql.length} chars; cleanup SQL ${cleanupSql.length} chars; curated IDs ${ids.length}`);
