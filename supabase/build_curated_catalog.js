// Generates repeatable Supabase seed SQL from official Steam store metadata.
const fs = require('node:fs');
const path = require('node:path');
const base = __dirname;
const existing = JSON.parse(fs.readFileSync(path.join(base, 'catalog-source.json'), 'utf8'));

// A focused set of well-known series, recent releases, and announced games.
const keepExisting = [
  292030,1245620,1086940,1091500,1174180,271590,489830,72850,377160,22380,
  620,220,730,550,275850,289070,1551360,
  391220,750920,203160,412020,286690,287390,782330,379720,
  883710,2050650,1196590,418370,2124490,814380,374320,570940,
  1888930,1593500,2322010,1151640,2420110,1817070,2651280,2215430,
  1659040,1172380,1774580,990080,1778820,553850,
  1903340,1771300,3280350,3017860,2947440,1285190,2246340,1941540,
  2001120,3764200,3357650,3321460,3768760,
  2769570,3558670,2590240,4354570
];
const newIds = [
  1237320,584400,2513280,1794960,2022670,
  287700,311340,2131630,2131640,2131650,2417610,
  339340,304240,952060,21690,221040,
  2161700,1113000,1687950,1382330,2963950
];
const info = {
  1237320:['Исследуйте открытые острова, сражайтесь с врагами и откройте новую историю Соника.',2022],
  584400:['Классический платформер о Сонике с новыми уровнями и героями.',2017],
  2513280:['Обновлённые приключения Соника и отдельная история Шэдоу.',2024],
  1794960:['Сборник классических игр о Сонике в обновлённом издании.',1991],
  2022670:['Платформер с командой Соника, трансформациями и скоростными уровнями.',2023],
  287700:['Стелс-экшен о Биг Боссе с открытыми миссиями и собственной базой.',2015],
  311340:['Пролог истории Metal Gear Solid V на военной базе Кэмп Омега.',2014],
  2131630:['Первая Metal Gear Solid в составе сборника Master Collection.',1998],
  2131640:['История Снейка и Райдена в переиздании Metal Gear Solid 2.',2001],
  2131650:['Приключение Нейкид Снейка в джунглях в переиздании Metal Gear Solid 3.',2004],
  2417610:['Современное переосмысление истории Снейка в джунглях.',2025],
  339340:['Приквел Resident Evil о расследовании в поезде и особняке.',2002],
  304240:['Классический Resident Evil в обновлённом HD-издании.',1996],
  952060:['Побег Джилл Валентайн из Раккун-Сити от Немезиса.',1999],
  21690:['Крис Редфилд и Шева Аломар расследуют новую биологическую угрозу.',2009],
  221040:['Несколько героев и сюжетных линий в масштабной истории Resident Evil 6.',2012],
  2161700:['Переработанная Persona 3: школьная жизнь и сражения в Тартаре.',2006],
  1113000:['Детективная RPG о загадочных исчезновениях в городке Инаба.',2008],
  1687950:['Команда Призрачных похитителей меняет сердца людей в Токио.',2019],
  1382330:['Продолжение истории Призрачных похитителей с поездкой по Японии.',2020],
  2963950:['Новое переосмысление Persona 4: расследование тайн Инабы, дружба и пошаговые сражения. Игра ещё не вышла.',2027]
};
const specialGenres = id => [2161700,1113000,1687950,1382330,2963950].includes(id) ? 'RPG · Приключения' :
  [1237320,584400,2513280,1794960,2022670].includes(id) ? 'Экшен · Приключения' :
  [339340,304240,952060,21690,221040].includes(id) ? 'Экшен · Хоррор' : 'Экшен · Стелс';

function steamDate(value) {
  const d = new Date(value + ' UTC');
  return Number.isNaN(d.getTime()) ? null : d.toISOString().slice(0,10);
}
function guideCards(id, title, description, upcoming) {
  const home = `https://store.steampowered.com/app/${id}/`;
  if (upcoming) return [{title:`Перед релизом: ${title}`,category:'Подготовка',content:`${description}\n\nИгра ещё не вышла. Проверенных прохождений и модов пока нет. Перед покупкой уточните дату выхода и платформы на официальной странице.`,url:home,source:'Официальная страница Steam'}];
  return [
    ['Прохождения','Walkthroughs','Выберите руководство для вашей версии игры и проверьте предупреждения о сюжетных спойлерах.'],
    ['Достижения','Achievements','В подборке сообщества проверьте пропускаемые достижения и условия сложности.'],
    ['Советы','Secrets','Сверяйте дату обновления руководства с версией игры.']
  ].map(([category,tag,note]) => ({title:`${category}: ${title}`,category,content:`${description}\n\nЭто подборка руководств Steam, а не скопированное прохождение. ${note}\n\nАвторы и оценки указаны на страницах источника.`,url:`https://steamcommunity.com/app/${id}/guides/?browsefilter=toprated&requiredtags%5B%5D=${tag}`,source:'Руководства сообщества Steam'}));
}
async function fetchGame(id) {
  const response = await fetch(`https://store.steampowered.com/api/appdetails?appids=${id}&l=english&cc=us`);
  if (!response.ok) throw Error(`${id}: HTTP ${response.status}`);
  const block = (await response.json())[id];
  if (!block?.success) throw Error(`${id}: missing store page`);
  const d = block.data;
  const upcoming = !!d.release_date?.coming_soon;
  const release = steamDate(d.release_date?.date || '') || (id === 2963950 ? '2027-02-17' : null);
  if (!release) throw Error(`${id}: unknown release date`);
  const source = `https://store.steampowered.com/app/${id}/`;
  const title = d.name.trim();
  const description = info[id][0];
  return {
    steam_app_id:id,title,description,release_date:release,developer:d.developers?.[0] || 'Издатель',
    platform:'Windows',cover_image:d.header_image || '',hero_image:d.screenshots?.[0]?.path_full || '',
    genre:specialGenres(id),publisher:d.publishers?.[0] || '',release_status:upcoming?'upcoming':'released',
    release_label:new Intl.DateTimeFormat('ru-RU',{day:'2-digit',month:'2-digit',year:'numeric',timeZone:'UTC'}).format(new Date(release+'T00:00:00Z')),
    first_release_year:info[id][1],source_url:source,popularity:0,critic_score:d.metacritic?.score || null,
    screenshots:(d.screenshots || []).slice(0,4).map((s,i)=>({url:s.path_full,title:`Официальный скриншот ${i+1}`})),
    trailers:(d.movies || []).slice(0,2).map(m=>({title:m.name,url:m.mp4?.max || m.mp4?.['480'] || '',thumbnail:m.thumbnail || '',source})).filter(x=>x.url),
    guides:guideCards(id,title,description,upcoming)
  };
}
async function main() {
  const additions=[];
  for (let i=0;i<newIds.length;i+=4) additions.push(...await Promise.all(newIds.slice(i,i+4).map(fetchGame)));
  const keep = existing.filter(x=>keepExisting.includes(x.steam_app_id));
  if (keep.length !== keepExisting.length) throw Error('An existing game ID is missing');
  if (keep.some(x=>x.genre.split(' · ').includes('Инди'))) throw Error('Indie game remained');
  fs.writeFileSync(path.join(base,'curated-catalog.json'),JSON.stringify([...keep,...additions],null,2));
  const seed = JSON.stringify(additions).replaceAll('$catalog$','$ca talog$');
  const sql = `-- GameKritik curated franchise additions. Apply after 005_large_catalog.sql.\nbegin;\n`+
`insert into public.users(name,email,password) values('GameKritik — редакция','editorial@gamekritik.invalid','!not-a-login!') on conflict do nothing;\n`+
`do $import$ declare item jsonb; media jsonb; gid integer; begin\n`+
`for item in select value from jsonb_array_elements($catalog$${seed}$catalog$::jsonb) loop\n`+
`insert into public.users(name,email,password) values(item->>'developer',md5(item->>'developer')||'@gamekritik.invalid','!not-a-login!') on conflict do nothing;\n`+
`insert into public.games(steam_app_id,title,description,release_date,developer,platform,cover_image,hero_image,genre,publisher,release_status,release_label,first_release_year,source_url,popularity,critic_score)\n`+
`values((item->>'steam_app_id')::integer,item->>'title',item->>'description',(item->>'release_date')::date,item->>'developer',item->>'platform',item->>'cover_image',item->>'hero_image',item->>'genre',item->>'publisher',item->>'release_status',item->>'release_label',(item->>'first_release_year')::integer,item->>'source_url',(item->>'popularity')::integer,(item->>'critic_score')::integer)\n`+
`on conflict(steam_app_id) do update set title=excluded.title,description=excluded.description,release_date=excluded.release_date,cover_image=excluded.cover_image,hero_image=excluded.hero_image,genre=excluded.genre,publisher=excluded.publisher,release_status=excluded.release_status,release_label=excluded.release_label,first_release_year=excluded.first_release_year,source_url=excluded.source_url,popularity=excluded.popularity,critic_score=excluded.critic_score returning game_id into gid;\n`+
`for media in select value from jsonb_array_elements(item->'screenshots') loop\n`+
`insert into public.screenshots(game_id,image_url,description) select gid,media->>'url',media->>'title' where not exists(select 1 from public.screenshots where game_id=gid and image_url=media->>'url'); end loop;\n`+
`for media in select value from jsonb_array_elements(item->'trailers') loop\n`+
`insert into public.trailers(game_id,title,video_url,thumbnail_url,source_url) select gid,media->>'title',media->>'url',media->>'thumbnail',media->>'source' where not exists(select 1 from public.trailers where game_id=gid and video_url=media->>'url'); end loop;\n`+
`for media in select value from jsonb_array_elements(item->'guides') loop\n`+
`insert into public.guides(user_id,game_id,title,content,category,author,source_url,cover_image,source_name) select (select user_id from public.users where email='editorial@gamekritik.invalid'),gid,media->>'title',media->>'content',media->>'category','GameKritik · подборка',media->>'url',item->>'cover_image',media->>'source' where not exists(select 1 from public.guides where game_id=gid and source_url=media->>'url'); end loop;\n`+
`end loop; end $import$;\ncommit;\nselect count(*) games from public.games;\n`;
  fs.writeFileSync(path.join(base,'006_franchise_catalog.sql'),sql);
  fs.writeFileSync(path.join(base,'curated-keep-ids.json'),JSON.stringify([...keepExisting,...newIds],null,2));
  console.log(`Existing: ${existing.length}, curated: ${keepExisting.length} + ${additions.length} = ${keep.length+additions.length}; SQL ${sql.length} chars`);
}
main().catch(e=>{console.error(e);process.exit(1)});
