-- Optional sample catalog matching the four game titles in Figma.
-- Ratings below reproduce the mockup; they are not measured user averages.
-- No images, fake reviews or real user accounts are added.
-- The existing games.developer FK points to users.name, so developer records
-- with reserved .invalid emails and no Auth account are required by this schema.
begin;
insert into public.users(name,email,password) values
 ('CD Projekt RED','cdpr@gamekritik.invalid','!not-a-login!'),
 ('FromSoftware','fromsoftware@gamekritik.invalid','!not-a-login!'),
 ('Larian Studios','larian@gamekritik.invalid','!not-a-login!')
on conflict do nothing;

insert into public.games(title,description,release_date,developer,platform,rating)
select x.* from (values
 ('The Witcher 3: Wild Hunt','Ведьмак 3: Дикая Охота — ролевая игра в открытом фэнтезийном мире. Вы играете за Геральта из Ривии, профессионального охотника на чудовищ. Исследуйте мир, выполняйте заказы и принимайте решения, которые влияют на историю.','2015-05-19'::date,'CD Projekt RED','PC · PlayStation · Xbox · Switch',9.8::double precision),
 ('Elden Ring','Отправляйтесь в Междуземье: исследуйте открытый мир, создайте своего героя и сразитесь с могущественными противниками.','2022-02-25'::date,'FromSoftware','PC · PlayStation · Xbox',9.6::double precision),
 ('Baldur''s Gate 3','Соберите отряд и отправляйтесь в приключение во вселенной Dungeons & Dragons. Пошаговые сражения, диалоги и ваши решения определяют судьбу героев.','2023-08-03'::date,'Larian Studios','PC · PlayStation · Xbox',9.5::double precision),
 ('Cyberpunk 2077','Ролевая игра в Найт-Сити. Создайте наёмника V, исследуйте город будущего и выберите свой путь в мире корпораций и кибернетических улучшений.','2020-12-10'::date,'CD Projekt RED','PC · PlayStation · Xbox',8.2::double precision)
) as x(title,description,release_date,developer,platform,rating)
where not exists(select 1 from public.games g where g.title=x.title);
commit;
