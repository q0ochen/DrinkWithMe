-- pgTAP: Freundschaften und Anfragen – RLS-Matrix für friendships/friend_requests sowie die RPCs
-- send_friend_request (neutrale Antwort, Gegenanfrage, Limit), respond_friend_request und remove_friend.
begin;
select plan(54);

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
create function pg_temp.befreunden(a text, b text) returns void language sql as $$
  insert into public.friendships (user_a, user_b)
  values (least(pg_temp.id(a), pg_temp.id(b)), greatest(pg_temp.id(a), pg_temp.id(b)))
$$;
create function pg_temp.befreundet(a text, b text) returns boolean language sql security definer as $$
  select exists (select 1 from public.friendships f
                 where f.user_a = least(pg_temp.id(a), pg_temp.id(b))
                   and f.user_b = greatest(pg_temp.id(a), pg_temp.id(b)))
$$;
create function pg_temp.anfrage(von text, an text) returns uuid language sql security definer as $$
  select r.id from public.friend_requests r where r.from_user = pg_temp.id(von) and r.to_user = pg_temp.id(an)
$$;
grant execute on all functions in schema pg_temp to public;

select pg_temp.nutzer('anna');
select pg_temp.nutzer('ben');
select pg_temp.nutzer('clara');
select pg_temp.nutzer('dora');
select pg_temp.nutzer('emil', bestaetigt => false);           -- E-Mail-Adresse nicht bestätigt
select pg_temp.nutzer('fritz');
insert into auth.users (id, email, email_confirmed_at, is_anonymous)  -- anonymes Konto mit Adresse
values (gen_random_uuid(), 'anonym@example.org', now(), true);

-- ---------------------------------------------------------------------------------------------------
-- send_friend_request: immer neutrale Antwort
-- ---------------------------------------------------------------------------------------------------
select pg_temp.als('anna');
select lives_ok($$ select public.send_friend_request('  BEN@Example.org ') $$,
  'Anfrage an Ben (Groß-/Kleinschreibung und Leerzeichen egal)');
select ok(pg_temp.anfrage('anna', 'ben') is not null, 'Die Anfrage Anna → Ben ist angelegt');
select lives_ok($$ select public.send_friend_request('ben@example.org') $$, 'Doppelte Anfrage: keine Fehlermeldung');
select lives_ok($$ select public.send_friend_request('niemand@example.org') $$, 'Unbekannte Adresse: keine Fehlermeldung');
select lives_ok($$ select public.send_friend_request('anna@example.org') $$, 'Eigene Adresse: keine Fehlermeldung');
select lives_ok($$ select public.send_friend_request('emil@example.org') $$, 'Unbestätigte Adresse: keine Fehlermeldung');
select lives_ok($$ select public.send_friend_request('anonym@example.org') $$, 'Anonymes Konto: keine Fehlermeldung');
select lives_ok($$ select public.send_friend_request('') $$, 'Leere Eingabe: keine Fehlermeldung');
select lives_ok($$ select public.send_friend_request(null) $$, 'NULL: keine Fehlermeldung');
select results_eq(
  $$ select public.send_friend_request('niemand@example.org')::text $$,
  $$ select public.send_friend_request('dora@example.org')::text $$,
  'Unbekannte und bekannte Adresse liefern dieselbe Antwort'
);
select pg_temp.als_postgres();
select set_eq(
  $$ select to_user from public.friend_requests where from_user = pg_temp.id('anna') $$,
  array[pg_temp.id('ben'), pg_temp.id('dora')],
  'Nur echte, bestätigte, fremde Konten bekommen eine Anfrage (keine Doppelten, nicht an sich selbst)'
);

-- ---------------------------------------------------------------------------------------------------
-- RLS friend_requests: Absender und Empfänger sehen die Anfrage, andere nicht
-- ---------------------------------------------------------------------------------------------------
select pg_temp.als('ben');
select set_eq($$ select from_user from public.friend_requests $$, array[pg_temp.id('anna')],
  'Ben (Empfänger) sieht die eingehende Anfrage');
select set_eq($$ select id from public.profiles $$, array[pg_temp.id('ben'), pg_temp.id('anna')],
  'Ben sieht das Profil der Absenderin');
select pg_temp.als('clara');
select is_empty($$ select id from public.friend_requests $$, 'Clara (unbeteiligt) sieht keine Anfragen');
select set_eq($$ select id from public.profiles $$, array[pg_temp.id('clara')],
  'Clara sieht weder Anna noch Ben');
select throws_ok(
  $$ insert into public.friend_requests (from_user, to_user) values (pg_temp.id('clara'), pg_temp.id('anna')) $$,
  '42501', null, 'Anfragen lassen sich nicht direkt anlegen (nur per RPC)'
);
select throws_ok(
  $$ update public.friend_requests set to_user = pg_temp.id('clara') $$,
  '42501', null, 'Anfragen lassen sich nicht ändern'
);
select pg_temp.als_gast();
select throws_ok($$ select id from public.friend_requests $$, '42501', null, 'Nicht angemeldet: kein Zugriff auf Anfragen');

-- ---------------------------------------------------------------------------------------------------
-- Gegenanfrage → sofort Freundschaft
-- ---------------------------------------------------------------------------------------------------
select pg_temp.als('ben');
select lives_ok($$ select public.send_friend_request('anna@example.org') $$, 'Ben schickt Anna eine Gegenanfrage');
select ok(pg_temp.befreundet('anna', 'ben'), 'Gegenanfrage: Anna und Ben sind sofort befreundet');
select ok(pg_temp.anfrage('anna', 'ben') is null and pg_temp.anfrage('ben', 'anna') is null,
  'Gegenanfrage: keine offene Anfrage mehr zwischen beiden');

select pg_temp.als('anna');
select lives_ok($$ select public.send_friend_request('ben@example.org') $$, 'Schon befreundet: keine Fehlermeldung');
select ok(pg_temp.anfrage('anna', 'ben') is null, 'Schon befreundet: keine neue Anfrage');

-- ---------------------------------------------------------------------------------------------------
-- RLS friendships: nur Beteiligte; Schreiben nur per RPC
-- ---------------------------------------------------------------------------------------------------
select pg_temp.als('anna');
select results_eq($$ select user_a, user_b from public.friendships $$,
  $$ values (pg_temp.id('anna'), pg_temp.id('ben')) $$, 'Anna sieht ihre Freundschaft mit Ben');
select pg_temp.als('ben');
select is((select count(*)::int from public.friendships), 1, 'Ben sieht seine Freundschaft mit Anna');
select pg_temp.als('clara');
select is_empty($$ select 1 from public.friendships $$, 'Clara (Fremde) sieht keine fremden Freundschaften');
select throws_ok(
  $$ insert into public.friendships (user_a, user_b) values (pg_temp.id('anna'), pg_temp.id('clara')) $$,
  '42501', null, 'Freundschaften lassen sich nicht direkt anlegen'
);
select pg_temp.als('anna');
select throws_ok($$ delete from public.friendships $$, '42501', null, 'Freundschaften lassen sich nicht direkt löschen');
select throws_ok($$ update public.friendships set created_at = now() $$, '42501', null, 'Freundschaften lassen sich nicht ändern');
select pg_temp.als_gast();
select throws_ok($$ select 1 from public.friendships $$, '42501', null, 'Nicht angemeldet: kein Zugriff auf Freundschaften');

-- ---------------------------------------------------------------------------------------------------
-- respond_friend_request: nur der Empfänger
-- ---------------------------------------------------------------------------------------------------
select pg_temp.als('clara');
select lives_ok($$ select public.send_friend_request('dora@example.org') $$, 'Clara fragt Dora an');

select pg_temp.als('anna');
select throws_ok(format('select public.respond_friend_request(%L, true)', pg_temp.anfrage('clara', 'dora')),
  'P0001', 'request_not_found', 'Unbeteiligte können eine fremde Anfrage nicht annehmen');
select pg_temp.als('clara');
select throws_ok(format('select public.respond_friend_request(%L, true)', pg_temp.anfrage('clara', 'dora')),
  'P0001', 'request_not_found', 'Die Absenderin kann ihre eigene Anfrage nicht annehmen');
select throws_ok(format('select public.respond_friend_request(%L, true)', gen_random_uuid()),
  'P0001', 'request_not_found', 'Unbekannte Anfrage');
select pg_temp.als('dora');
select lives_ok(format('select public.respond_friend_request(%L, true)', pg_temp.anfrage('clara', 'dora')),
  'Dora nimmt Claras Anfrage an');
select ok(pg_temp.befreundet('clara', 'dora'), 'Annehmen: Clara und Dora sind befreundet');
select ok(pg_temp.anfrage('clara', 'dora') is null, 'Annehmen: die Anfrage ist gelöscht');

-- Ablehnen (Anna hatte Dora oben angefragt)
select lives_ok(format('select public.respond_friend_request(%L, false)', pg_temp.anfrage('anna', 'dora')),
  'Dora lehnt Annas Anfrage ab');
select ok(pg_temp.anfrage('anna', 'dora') is null, 'Ablehnen: die Anfrage ist gelöscht');
select ok(not pg_temp.befreundet('anna', 'dora'), 'Ablehnen: keine Freundschaft');

-- Zurückziehen per DELETE: nur der Absender
select pg_temp.als('anna');
select lives_ok($$ select public.send_friend_request('fritz@example.org') $$, 'Anna fragt Fritz an');
select pg_temp.als('fritz');
select is_empty($$ delete from public.friend_requests returning id $$, 'Der Empfänger kann eine Anfrage nicht löschen (nur ablehnen)');
select pg_temp.als('anna');
select isnt_empty($$ delete from public.friend_requests where to_user = pg_temp.id('fritz') returning id $$,
  'Die Absenderin zieht ihre Anfrage zurück');
select ok(pg_temp.anfrage('anna', 'fritz') is null, 'Zurückziehen: die Anfrage ist gelöscht');

-- ---------------------------------------------------------------------------------------------------
-- remove_friend: löscht nur die eigene Freundschaft, idempotent
-- ---------------------------------------------------------------------------------------------------
select pg_temp.als('anna');
select lives_ok(format('select public.remove_friend(%L)', pg_temp.id('dora')),
  'Anna „entfreundet“ Dora, mit der sie gar nicht befreundet ist');
select ok(pg_temp.befreundet('clara', 'dora'), 'Fremde Freundschaften (Clara ↔ Dora) bleiben unberührt');
select pg_temp.als('ben');
select lives_ok(format('select public.remove_friend(%L)', pg_temp.id('anna')), 'Ben beendet die Freundschaft mit Anna');
select ok(not pg_temp.befreundet('anna', 'ben'), 'Die Freundschaft ist gelöscht');
select lives_ok(format('select public.remove_friend(%L)', pg_temp.id('anna')), 'Nochmals entfernen ist kein Fehler (idempotent)');

-- ---------------------------------------------------------------------------------------------------
-- Limit: 20 Anfragen pro Nutzer und Tag
-- ---------------------------------------------------------------------------------------------------
select pg_temp.als('fritz');
select lives_ok(
  $$ select public.send_friend_request('unbekannt' || n || '@example.org') from generate_series(1, 20) n $$,
  '20 Anfragen an einem Tag sind erlaubt (auch an unbekannte Adressen)'
);
select throws_ok($$ select public.send_friend_request('ben@example.org') $$, 'P0001', 'rate_limited',
  'Die 21. Anfrage am selben Tag: rate_limited');
select pg_temp.als_postgres();
update public.rate_limits set window_start = now() - interval '1 day 1 second'
 where user_id = pg_temp.id('fritz') and action = 'send_friend_request';
select pg_temp.als('fritz');
select lives_ok($$ select public.send_friend_request('ben@example.org') $$, 'Nach Ablauf des Tages geht es wieder');

-- ---------------------------------------------------------------------------------------------------
-- Ohne Anmeldung
-- ---------------------------------------------------------------------------------------------------
select pg_temp.als_gast();
select throws_ok($$ select public.send_friend_request('ben@example.org') $$, '42501', null,
  'anon darf send_friend_request nicht ausführen');
select pg_temp.als_postgres();
set local role authenticated;   -- Rolle authenticated, aber ohne sub-Claim
select throws_ok($$ select public.send_friend_request('ben@example.org') $$, 'P0001', 'not_authenticated',
  'Ohne Nutzer-ID im JWT: not_authenticated');

select * from finish();
rollback;
