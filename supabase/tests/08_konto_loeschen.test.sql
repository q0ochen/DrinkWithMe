-- pgTAP: Konto löschen – wird der Auth-Nutzer gelöscht (Edge Function delete-account über die Admin-API),
-- verschwinden per ON DELETE CASCADE alle seine Daten; die Daten anderer Nutzer bleiben.
begin;
select plan(11);

-- ---------------------------------------------------------------------------------------------------
-- Testumgebung (in jeder Testdatei gleich; alles liegt in pg_temp und verschwindet mit dem ROLLBACK)
-- ---------------------------------------------------------------------------------------------------
create function pg_temp.id(kurzname text) returns uuid language sql immutable as $$
  select case kurzname
    when 'anna'  then 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
    when 'ben'   then 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'
    when 'clara' then 'cccccccc-cccc-4ccc-8ccc-cccccccccccc'
    when 'dora'  then 'dddddddd-dddd-4ddd-8ddd-dddddddddddd'
    when 'emil'  then 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee'
    when 'fritz' then 'ffffffff-ffff-4fff-8fff-ffffffffffff'
  end::uuid
$$;
-- Wechselt wie PostgREST die Rolle und setzt die JWT-Claims, die auth.uid() liest.
create function pg_temp.als(kurzname text) returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims',
    json_build_object('sub', pg_temp.id(kurzname), 'role', 'authenticated')::text, true);
  set local role authenticated;
end $$;
create function pg_temp.als_postgres() returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims', '', true);
  reset role;
end $$;
-- Legt einen Auth-Nutzer an (wie GoTrue nach der Google-Anmeldung); der Trigger erzeugt das Profil.
create function pg_temp.nutzer(kurzname text, meta jsonb default null, bestaetigt boolean default true)
returns uuid language sql as $$
  insert into auth.users (id, aud, role, email, email_confirmed_at, raw_user_meta_data)
  values (pg_temp.id(kurzname), 'authenticated', 'authenticated', kurzname || '@example.org',
          case when bestaetigt then now() end,
          coalesce(meta, jsonb_build_object('full_name', initcap(kurzname))))
  returning id
$$;
grant execute on all functions in schema pg_temp to public;

select pg_temp.nutzer('anna');
select pg_temp.nutzer('ben');
select pg_temp.nutzer('clara');

-- Anna nutzt alles: Freundschaft, Anfragen (aus- und eingehend), Link, Ereignis, Push-Abo, Limit-Zähler.
select pg_temp.als('anna');
select public.send_friend_request('ben@example.org');
select public.create_invite_link() is not null as link_erzeugt;  -- Token selbst nicht ausgeben (TAP-Ausgabe)
insert into public.drink_events (id, name, icon, category, volume_ml, consumed_at)
values ('00000000-0000-4000-8000-0000000000a1', 'Wasser', 'water', 'WATER', 250, now());
insert into public.push_subscriptions (endpoint, p256dh, auth, platform)
values ('https://ntfy.example.org/upAnna', 'B' || repeat('A', 86), repeat('b', 22), 'unifiedpush');
select pg_temp.als('ben');
select public.send_friend_request('anna@example.org');       -- Gegenanfrage → Freundschaft
insert into public.drink_events (id, name, icon, category, volume_ml, consumed_at)
values ('00000000-0000-4000-8000-0000000000b1', 'Tee', 'tea', 'TEA', 250, now());
select pg_temp.als('clara');
select public.send_friend_request('anna@example.org');       -- eingehende Anfrage an Anna
select pg_temp.als_postgres();

select is((select count(*)::int from public.friendships), 1, 'Vorher: Anna und Ben sind befreundet');

-- Das macht die Admin-API von GoTrue beim Löschen des Kontos.
delete from auth.users where id = pg_temp.id('anna');

select is_empty($$ select 1 from public.profiles where id = pg_temp.id('anna') $$, 'Profil gelöscht');
select is_empty($$ select 1 from public.friendships $$, 'Freundschaften gelöscht');
select is_empty($$ select 1 from public.friend_requests where pg_temp.id('anna') in (from_user, to_user) $$,
  'Anfragen (ein- und ausgehend) gelöscht');
select is_empty($$ select 1 from public.invite_links where owner = pg_temp.id('anna') $$, 'Einladungs-Link gelöscht');
select is_empty($$ select 1 from public.drink_events where author_id = pg_temp.id('anna') $$, 'Geteilte Ereignisse gelöscht');
select is_empty($$ select 1 from public.push_subscriptions where user_id = pg_temp.id('anna') $$, 'Push-Abos gelöscht');
select is_empty($$ select 1 from public.rate_limits where user_id = pg_temp.id('anna') $$, 'Limit-Zähler gelöscht');

select set_eq($$ select id from public.profiles $$, array[pg_temp.id('ben'), pg_temp.id('clara')],
  'Die Profile der anderen bleiben');
select set_eq($$ select id from public.drink_events $$, array['00000000-0000-4000-8000-0000000000b1'::uuid],
  'Die Ereignisse der anderen bleiben');
select is((select count(*)::int from public.rate_limits where user_id = pg_temp.id('ben')), 1,
  'Die Limit-Zähler der anderen bleiben');

select * from finish();
rollback;
