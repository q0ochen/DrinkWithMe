-- pgTAP: Aufruflimits (rate_limits, consume_rate_limit) und tägliches Aufräumen per pg_cron
-- (drink_events und friend_requests älter als 30 Tage, abgelaufene invite_links, veraltete Zähler und
-- Vermerke über entfernte Freunde).
begin;
select plan(23);

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
create function pg_temp.als_gast() returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims', '{"role":"anon"}', true);
  set local role anon;
end $$;
create function pg_temp.als_server() returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claims', '{"role":"service_role"}', true);
  set local role service_role;
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

-- ---------------------------------------------------------------------------------------------------
-- rate_limits: kein Client-Zugriff
-- ---------------------------------------------------------------------------------------------------
select pg_temp.als('anna');
select throws_ok($$ select 1 from public.rate_limits $$, '42501', null, 'Clients können rate_limits nicht lesen');
select throws_ok($$ delete from public.rate_limits $$, '42501', null, 'Clients können ihr Limit nicht zurücksetzen');
select throws_ok(
  $$ select public.consume_rate_limit(pg_temp.id('anna'), 'notify_friends', 1000, 1) $$,
  '42501', null, 'Clients können consume_rate_limit nicht aufrufen'
);
select pg_temp.als_gast();
select throws_ok($$ select 1 from public.rate_limits $$, '42501', null, 'anon kann rate_limits nicht lesen');

-- ---------------------------------------------------------------------------------------------------
-- consume_rate_limit (Service-Rolle, z. B. notify-friends: 60 pro Stunde)
-- ---------------------------------------------------------------------------------------------------
select pg_temp.als_server();
select results_eq(
  $$ select public.consume_rate_limit(pg_temp.id('anna'), 'notify_friends', 3, 3600) from generate_series(1, 4) $$,
  $$ values (true), (true), (true), (false) $$,
  'Drei Aufrufe sind erlaubt, der vierte im selben Fenster nicht'
);
select is(public.consume_rate_limit(pg_temp.id('ben'), 'notify_friends', 3, 3600), true,
  'Das Limit gilt pro Nutzer');
select is(public.consume_rate_limit(pg_temp.id('anna'), 'accept_invite', 3, 3600), true,
  'Das Limit gilt pro Aktion');
select pg_temp.als_postgres();
update public.rate_limits set window_start = now() - interval '3600 seconds'
 where user_id = pg_temp.id('anna') and action = 'notify_friends';
select pg_temp.als_server();
select is(public.consume_rate_limit(pg_temp.id('anna'), 'notify_friends', 3, 3600), true,
  'Nach Ablauf des Fensters beginnt die Zählung neu');
select results_eq(
  $$ select window_start, count from public.rate_limits
      where user_id = pg_temp.id('anna') and action = 'notify_friends' $$,
  $$ values (now(), 1) $$,
  'Neues Fenster beginnt jetzt mit Zähler 1'
);
select throws_ok($$ select public.consume_rate_limit(pg_temp.id('anna'), 'x', 0, 60) $$, '22023', null,
  'Ungültige Grenze wird abgelehnt');
select throws_ok($$ select public.consume_rate_limit(pg_temp.id('anna'), 'Ungültig!', 5, 60) $$, '23514', null,
  'Ungültiger Aktionsname wird abgelehnt');

-- ---------------------------------------------------------------------------------------------------
-- Aufräumen
-- ---------------------------------------------------------------------------------------------------
select pg_temp.als_postgres();
insert into public.drink_events (id, author_id, name, icon, category, volume_ml, consumed_at, created_at) values
  ('00000000-0000-4000-8000-000000000031', pg_temp.id('anna'), 'Alt', 'water', 'WATER', 250,
   now() - interval '30 days 1 second', now() - interval '30 days'),
  ('00000000-0000-4000-8000-000000000029', pg_temp.id('anna'), 'Neu genug', 'water', 'WATER', 250,
   now() - interval '29 days', now() - interval '29 days');
insert into public.invite_links (token, owner, created_at, expires_at) values
  ('abgelaufenAAAAAAAAAAAAAAAAAAAAAA', pg_temp.id('anna'), now() - interval '15 days', now() - interval '1 day'),
  ('gueltigBBBBBBBBBBBBBBBBBBBBBBBBB', pg_temp.id('ben'), now(), now() + interval '14 days');
insert into public.rate_limits (user_id, action, window_start, count) values
  (pg_temp.id('ben'), 'send_friend_request', now() - interval '3 days', 20);
-- Anfragen: mit und ohne Empfänger, älter und jünger als 30 Tage
insert into public.friend_requests (from_user, to_user, email_hash, email_hint, created_at) values
  (pg_temp.id('anna'), pg_temp.id('ben'), private.friend_request_hash(pg_temp.id('anna'), 'ben@example.org'),
   'b***@example.org', now() - interval '30 days 1 second'),
  (pg_temp.id('anna'), null, private.friend_request_hash(pg_temp.id('anna'), 'alt@example.org'),
   'a***@example.org', now() - interval '30 days 1 second'),
  (pg_temp.id('ben'), null, private.friend_request_hash(pg_temp.id('ben'), 'neu@example.org'),
   'n***@example.org', now() - interval '29 days'),
  (pg_temp.id('ben'), pg_temp.id('anna'), private.friend_request_hash(pg_temp.id('ben'), 'anna@example.org'),
   'a***@example.org', now() - interval '29 days');
-- Vermerke über entfernte Freunde: nach 14 Tagen ist jeder ältere Link abgelaufen
insert into private.friend_removals (remover, removed, removed_at) values
  (pg_temp.id('anna'), pg_temp.id('ben'), now() - interval '14 days 1 second'),
  (pg_temp.id('ben'), pg_temp.id('anna'), now() - interval '13 days');

select lives_ok($$ select private.cleanup_expired_data() $$, 'Aufräumen läuft ohne Fehler');
select set_eq($$ select id from public.drink_events $$, array['00000000-0000-4000-8000-000000000029'::uuid],
  'Ereignisse älter als 30 Tage sind gelöscht, jüngere bleiben');
select set_eq($$ select token from public.invite_links $$, array['gueltigBBBBBBBBBBBBBBBBBBBBBBBBB'],
  'Abgelaufene Einladungs-Links sind gelöscht, gültige bleiben');
select is_empty(
  $$ select 1 from public.rate_limits where user_id = pg_temp.id('ben') and action = 'send_friend_request' $$,
  'Veraltete Limit-Zähler sind gelöscht'
);
select is((select count(*)::int from public.rate_limits where user_id = pg_temp.id('anna')), 2,
  'Aktuelle Limit-Zähler bleiben');
select set_eq($$ select email_hint from public.friend_requests $$, array['n***@example.org', 'a***@example.org'],
  'Anfragen älter als 30 Tage sind gelöscht – mit und ohne Empfänger gleich (verrät nichts)');
select set_eq($$ select remover from private.friend_removals $$, array[pg_temp.id('ben')],
  'Vermerke über entfernte Freunde älter als 14 Tage sind gelöscht, jüngere bleiben');

select results_eq(
  $$ select schedule, command from cron.job where jobname = 'drinkwithme-aufraeumen' $$,
  $$ values ('17 3 * * *'::text, 'select private.cleanup_expired_data()'::text) $$,
  'pg_cron führt das Aufräumen täglich um 03:17 UTC aus'
);
select is((select active from cron.job where jobname = 'drinkwithme-aufraeumen'), true, 'Der Aufräum-Job ist aktiv');

select pg_temp.als('anna');
select throws_ok($$ select private.cleanup_expired_data() $$, '42501', null, 'Clients können das Aufräumen nicht auslösen');
select pg_temp.als_gast();
select throws_ok($$ select private.cleanup_expired_data() $$, '42501', null, 'anon kann das Aufräumen nicht auslösen');
select pg_temp.als('anna');
select throws_ok($$ select private.make_friends(pg_temp.id('anna'), pg_temp.id('ben')) $$, '42501', null,
  'Interne Hilfsfunktionen sind für Clients nicht aufrufbar');

select * from finish();
rollback;
