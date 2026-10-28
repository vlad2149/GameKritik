-- Only the signed-in user's own GameKritik reviews are returned to the profile.
begin;
create or replace function public.gk_my_reviews() returns jsonb
language sql stable security definer set search_path = '' as $$
  select coalesce(jsonb_agg(jsonb_build_object(
    'review_id', r.review_id,
    'game_id', r.game_id,
    'text', r.text,
    'rating', r.rating,
    'date', r.date
  ) order by r.date desc), '[]'::jsonb)
  from public.reviews r
  where r.user_id = (public.gk_profile()->>'user_id')::integer;
$$;
revoke all on function public.gk_my_reviews() from public, anon;
grant execute on function public.gk_my_reviews() to authenticated;
notify pgrst, 'reload schema';
commit;
