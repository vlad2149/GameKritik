-- Review before running: removes only unengaged games outside curated list.
-- Existing favorites, reviews, viewing history, user guides, and mod rows protect a game from removal.
begin;
create temporary table gk_retired_games on commit drop as
select g.game_id,g.title from public.games g
where g.steam_app_id is not null and g.steam_app_id <> all(array[292030,1245620,1086940,1091500,1174180,271590,489830,72850,377160,22380,620,220,730,550,275850,289070,1551360,391220,750920,203160,412020,286690,287390,782330,379720,883710,2050650,1196590,418370,2124490,814380,374320,570940,1888930,1593500,2322010,1151640,2420110,1817070,2651280,2215430,1659040,1172380,1774580,990080,1778820,553850,1903340,1771300,3280350,3017860,2947440,1285190,2246340,1941540,2001120,3764200,3357650,3321460,3768760,2769570,3558670,2590240,4354570,1237320,584400,2513280,1794960,2022670,287700,311340,2131630,2131640,2131650,2417610,339340,304240,952060,21690,221040,2161700,1113000,1687950,1382330,2963950]::integer[])
and not exists(select 1 from public.favorites f where f.game_id=g.game_id)
and not exists(select 1 from public.reviews r where r.game_id=g.game_id)
and not exists(select 1 from public.mods m where m.game_id=g.game_id)
and not exists(select 1 from public.game_history h where h.game_id=g.game_id)
and not exists(select 1 from public.guides d where d.game_id=g.game_id and d.user_id <> (select user_id from public.users where email='editorial@gamekritik.invalid' limit 1));
delete from public.guides where game_id in (select game_id from gk_retired_games);
delete from public.screenshots where game_id in (select game_id from gk_retired_games);
delete from public.trailers where game_id in (select game_id from gk_retired_games);
delete from public.games where game_id in (select game_id from gk_retired_games);
commit;
select count(*) remaining_games from public.games;
