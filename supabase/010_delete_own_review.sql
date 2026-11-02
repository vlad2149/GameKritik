-- Delete only the current user's review and recalculate the game's rating.
begin;
create or replace function public.gk_delete_review(p_review_id integer) returns void
language plpgsql security definer set search_path = '' as $$
declare owner_id integer; target_game_id integer;
begin
  owner_id := (public.gk_profile()->>'user_id')::integer;
  select game_id into target_game_id
  from public.reviews where review_id = p_review_id and user_id = owner_id;
  if target_game_id is null then raise exception 'Отзыв не найден или недоступен'; end if;
  perform 1 from public.games where game_id = target_game_id for update;
  delete from public.reviews where review_id = p_review_id and user_id = owner_id;
  update public.games set rating =
    (select avg(rating) from public.reviews where game_id = target_game_id)
  where game_id = target_game_id;
end $$;
revoke all on function public.gk_delete_review(integer) from public, anon;
grant execute on function public.gk_delete_review(integer) to authenticated;
notify pgrst, 'reload schema';
commit;
