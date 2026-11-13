-- pgTAP: Freundschaften und Anfragen – RLS-Matrix für friendships/friend_requests sowie die RPCs
-- send_friend_request (neutrale Antwort, für jede Adresse genau eine Zeile, Gegenanfrage, Limit),
-- list_outgoing_friend_requests, cancel_friend_request, respond_friend_request und remove_friend.
-- Kernanforderung: Wer eine Anfrage schickt, erfährt NICHT, ob es zur Adresse ein Konto gibt.
begin;
select plan(95);

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
    when 'gustav' then '99999999-9999-4999-8999-999999999999'
    when 'hans'  then '12121212-1212-4121-8121-121212121212'
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
-- Kennung einer ausgehenden Anfrage von `von` mit dem maskierten Hinweis `hinweis` (wie in der App-Liste).
create function pg_temp.ausgehend(von text, hinweis text) returns uuid language sql security definer as $$
  select r.id from public.friend_requests r where r.from_user = pg_temp.id(von) and r.email_hint = hinweis
$$;
-- Alles, was eine Person über ihre Anfragen und andere Konten erfährt – als Text und ohne ihre eigene ID, damit
-- sich die Sicht zweier Absender direkt vergleichen lässt.
create function pg_temp.sicht(kurzname text) returns text language plpgsql as $$
declare
  ergebnis text;
begin
  perform pg_temp.als(kurzname);
  select format('ausgehend=%s; tabelle=%s; profile=%s; freunde=%s',
           (select coalesce(string_agg(o.email_hint || ' ' || (o.created_at = now())::text, ', '
                                       order by o.email_hint), '')
              from public.list_outgoing_friend_requests() o),
           (select count(*) from public.friend_requests),
           (select count(*) from public.profiles p where p.id <> pg_temp.id(kurzname)),
           (select count(*) from public.friendships))
    into ergebnis;
  perform pg_temp.als_postgres();
  return ergebnis;
end $$;
grant execute on all functions in schema pg_temp to public;

select pg_temp.nutzer('anna');
select pg_temp.nutzer('ben');
select pg_temp.nutzer('clara');
select pg_temp.nutzer('dora');
select pg_temp.nutzer('emil', bestaetigt => false);           -- E-Mail-Adresse nicht bestätigt
select pg_temp.nutzer('fritz');
select pg_temp.nutzer('gustav');
insert into auth.users (id, email, email_confirmed_at, is_anonymous)  -- anonymes Konto mit Adresse
values (gen_random_uuid(), 'anonym@example.org', now(), true);

-- ---------------------------------------------------------------------------------------------------
-- send_friend_request: immer neutrale Antwort, für jede Adresse genau eine Zeile
-- ---------------------------------------------------------------------------------------------------
select pg_temp.als('anna');
select lives_ok($$ select public.send_friend_request('  BEN@Example.org ') $$,
  'Anfrage an Ben (Groß-/Kleinschreibung und Leerzeichen egal)');
select ok(pg_temp.anfrage('anna', 'ben') is not null, 'Die Anfrage Anna → Ben erreicht Ben');
select lives_ok($$ select public.send_friend_request('ben@example.org') $$, 'Doppelte Anfrage: keine Fehlermeldung');
select lives_ok($$ select public.send_friend_request('niemand@example.org') $$, 'Unbekannte Adresse: keine Fehlermeldung');
select lives_ok($$ select public.send_friend_request('anna@example.org') $$, 'Eigene Adresse: keine Fehlermeldung');
select lives_ok($$ select public.send_friend_request('emil@example.org') $$, 'Unbestätigte Adresse: keine Fehlermeldung');
select lives_ok($$ select public.send_friend_request('anonym@example.org') $$, 'Anonymes Konto: keine Fehlermeldung');
select lives_ok($$ select public.send_friend_request('') $$, 'Leere Eingabe: keine Fehlermeldung');
select lives_ok($$ select public.send_friend_request(null) $$, 'NULL: keine Fehlermeldung');
select lives_ok($$ select public.send_friend_request('keine-adresse') $$, 'Keine E-Mail-Adresse: keine Fehlermeldung');
select results_eq(
  $$ select public.send_friend_request('niemand@example.org')::text $$,
  $$ select public.send_friend_request('dora@example.org')::text $$,
  'Unbekannte und bekannte Adresse liefern dieselbe Antwort'
);
select bag_eq(
  $$ select email_hint from public.list_outgoing_friend_requests() $$,
  array['b***@example.org', 'n***@example.org', 'a***@example.org', 'e***@example.org', 'a***@example.org',
        'd***@example.org'],
  'Anna sieht für jede Adresse genau eine ausgehende Anfrage – egal ob es dazu ein Konto gibt'
);
select results_eq(
  $$ select count(*)::int from public.list_outgoing_friend_requests() where created_at = now() and id is not null $$,
  $$ values (6) $$,
  'Jede ausgehende Anfrage hat ID und Zeitpunkt'
);
select pg_temp.als_postgres();
select set_eq(
  $$ select to_user from public.friend_requests where from_user = pg_temp.id('anna') and to_user is not null $$,
  array[pg_temp.id('ben'), pg_temp.id('dora')],
  'Nur echte, bestätigte, fremde Konten werden erreicht (nicht an sich selbst, unbestätigte oder anonyme)'
);
select is(
  (select count(*)::int from public.friend_requests
    where from_user = pg_temp.id('anna') and octet_length(email_hash) = 32 and email_hint ~ '^.\*\*\*@'),
  6,
  'Gespeichert sind nur eine Kennung (HMAC) und ein maskierter Hinweis, nie die Adresse'
);
select is_empty(
  $$ select 1 from public.friend_requests where email_hint ilike '%niemand%' or email_hint ilike '%ben@%' $$,
  'Keine vollständige Adresse in der Datenbank'
);

-- ---------------------------------------------------------------------------------------------------
-- Keine Auskunft über Konten: Absender sehen bei registrierter und unbekannter Adresse dasselbe
-- ---------------------------------------------------------------------------------------------------
select pg_temp.als('fritz');
select public.send_friend_request('dora@example.org');      -- zu dieser Adresse gibt es ein Konto
select pg_temp.als('gustav');
select public.send_friend_request('dieter@example.org');    -- zu dieser nicht
select pg_temp.als_postgres();
select is(pg_temp.sicht('fritz'), pg_temp.sicht('gustav'),
  'Registrierte und unbekannte Adresse: der Absender sieht genau dasselbe');
select is(pg_temp.sicht('fritz'), 'ausgehend=d***@example.org true; tabelle=0; profile=0; freunde=0',
  'Der Absender sieht nur ID, Zeitpunkt und maskierte Adresse – keine Zeile in der Tabelle, kein Profil');

select pg_temp.als('fritz');
select is_empty($$ select to_user from public.friend_requests where from_user = pg_temp.id('fritz') $$,
  'Der Absender kann den Empfänger (to_user) seiner Anfrage nicht lesen');
select is_empty($$ select 1 from public.profiles where id = pg_temp.id('dora') $$,
  'Der Absender kann das Profil der Empfängerin nicht lesen');
select throws_ok($$ select email_hash from public.friend_requests $$, '42501', null,
  'Die Kennung der Adresse ist nicht lesbar');
select throws_ok($$ select email_hint from public.friend_requests $$, '42501', null,
  'Der Hinweis ist nur über list_outgoing_friend_requests() lesbar');
select throws_ok($$ select 1 from public.friend_requests where email_hash is not null $$, '42501', null,
  'Auch Filtern nach der Kennung ist nicht möglich');

select pg_temp.als('dora');
select set_eq($$ select from_user from public.friend_requests $$, array[pg_temp.id('anna'), pg_temp.id('fritz')],
  'Dora (Empfängerin) sieht beide eingehenden Anfragen');
select results_eq($$ select display_name from public.profiles where id = pg_temp.id('fritz') $$,
  $$ values ('Fritz'::text) $$, 'Dora (Empfängerin) sieht den Namen des Absenders');
select is_empty($$ select 1 from public.list_outgoing_friend_requests() $$,
  'Eingehende Anfragen stehen nicht in der Liste der ausgehenden');

-- ---------------------------------------------------------------------------------------------------
-- RLS friend_requests: nur Empfänger lesen; Schreiben nur per RPC
-- ---------------------------------------------------------------------------------------------------
select pg_temp.als('ben');
select set_eq($$ select from_user from public.friend_requests $$, array[pg_temp.id('anna')],
  'Ben (Empfänger) sieht die eingehende Anfrage');
select set_eq($$ select id from public.profiles $$, array[pg_temp.id('ben'), pg_temp.id('anna')],
  'Ben sieht das Profil der Absenderin');
select pg_temp.als('anna');
select is_empty($$ select id from public.friend_requests $$,
  'Anna (Absenderin) sieht ihre ausgehenden Anfragen nicht in der Tabelle');
select set_eq($$ select id from public.profiles $$, array[pg_temp.id('anna')],
  'Anna sieht vor der Annahme weder Ben noch Dora');
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
select pg_temp.als('ben');
select throws_ok($$ delete from public.friend_requests $$, '42501', null,
  'Anfragen lassen sich nicht direkt löschen (Zurückziehen per RPC)');
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
select ok(pg_temp.ausgehend('anna', 'b***@example.org') is null,
  'Annas erledigte Anfrage an Ben ist aus ihrer Liste verschwunden');

select lives_ok($$ select public.send_friend_request('ben@example.org') $$, 'Schon befreundet: keine Fehlermeldung');
select ok(pg_temp.anfrage('anna', 'ben') is null, 'Schon befreundet: die Anfrage erreicht Ben nicht');
select ok(pg_temp.ausgehend('anna', 'b***@example.org') is not null,
  'Schon befreundet: trotzdem eine Zeile wie bei jeder anderen Adresse (verrät keine Adressen von Freunden)');

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
select throws_ok(format('select public.respond_friend_request(%L, true)', pg_temp.ausgehend('anna', 'n***@example.org')),
  'P0001', 'request_not_found', 'Eine Anfrage ohne Empfänger kann niemand annehmen');
select pg_temp.als('dora');
select lives_ok(format('select public.respond_friend_request(%L, true)', pg_temp.anfrage('clara', 'dora')),
  'Dora nimmt Claras Anfrage an');
select ok(pg_temp.befreundet('clara', 'dora'), 'Annehmen: Clara und Dora sind befreundet');
select ok(pg_temp.ausgehend('clara', 'd***@example.org') is null, 'Annehmen: die Anfrage ist gelöscht');

-- Ablehnen (Anna hatte Dora oben angefragt): Für Anna sieht die Anfrage danach aus wie jede andere.
select pg_temp.ausgehend('anna', 'd***@example.org') as abgelehnt \gset
select lives_ok(format('select public.respond_friend_request(%L, false)', :'abgelehnt'),
  'Dora lehnt Annas Anfrage ab');
select ok(not pg_temp.befreundet('anna', 'dora'), 'Ablehnen: keine Freundschaft');
select is_empty($$ select 1 from public.friend_requests where from_user = pg_temp.id('anna') $$,
  'Ablehnen: Dora sieht Annas Anfrage nicht mehr');
select is_empty($$ select 1 from public.profiles where id = pg_temp.id('anna') $$,
  'Ablehnen: Dora sieht Annas Profil nicht mehr');
select throws_ok(format('select public.respond_friend_request(%L, true)', :'abgelehnt'),
  'P0001', 'request_not_found', 'Eine abgelehnte Anfrage lässt sich nicht nachträglich annehmen');
select pg_temp.als('anna');
select ok(pg_temp.ausgehend('anna', 'd***@example.org') = :'abgelehnt'::uuid,
  'Ablehnen: Anna sieht ihre Anfrage unverändert (kein Hinweis, dass es das Konto gibt)');
select lives_ok($$ select public.send_friend_request('dora@example.org') $$, 'Anna fragt Dora erneut an');
select pg_temp.als_postgres();
select results_eq(
  format('select to_user, declined_at is not null from public.friend_requests where id = %L', :'abgelehnt'),
  $$ values (null::uuid, true) $$,
  'Erneutes Anfragen nach dem Ablehnen erreicht Dora nicht wieder'
);

-- ---------------------------------------------------------------------------------------------------
-- Erneutes Anfragen erreicht ein Konto, das es beim ersten Mal noch nicht gab
-- ---------------------------------------------------------------------------------------------------
select pg_temp.als('gustav');
select public.send_friend_request('spaeter@example.org');
select pg_temp.ausgehend('gustav', 's***@example.org') as spaeter \gset
select pg_temp.als_postgres();
insert into auth.users (id, aud, role, email, email_confirmed_at, raw_user_meta_data)
values (pg_temp.id('hans'), 'authenticated', 'authenticated', 'spaeter@example.org', now(), '{"full_name": "Hans"}');
select pg_temp.als('gustav');
select lives_ok($$ select public.send_friend_request('Spaeter@example.org') $$, 'Gustav fragt dieselbe Adresse noch einmal an');
select is(pg_temp.ausgehend('gustav', 's***@example.org'), :'spaeter'::uuid, 'Es bleibt dieselbe Anfrage');
select pg_temp.als('hans');
select set_eq($$ select id from public.friend_requests $$, array[:'spaeter'::uuid],
  'Die Anfrage erreicht jetzt das neue Konto');

-- ---------------------------------------------------------------------------------------------------
-- Zurückziehen (cancel_friend_request): nur der Absender, idempotent
-- ---------------------------------------------------------------------------------------------------
select pg_temp.als('anna');
select lives_ok($$ select public.send_friend_request('fritz@example.org') $$, 'Anna fragt Fritz an');
select pg_temp.ausgehend('anna', 'f***@example.org') as an_fritz \gset
select pg_temp.als('fritz');
select lives_ok(format('select public.cancel_friend_request(%L)', :'an_fritz'),
  'Der Empfänger ruft cancel_friend_request auf: kein Fehler …');
select set_eq($$ select id from public.friend_requests $$, array[:'an_fritz'::uuid],
  '… aber die Anfrage bleibt (der Empfänger kann nur ablehnen)');
select pg_temp.als('clara');
select lives_ok(format('select public.cancel_friend_request(%L)', :'an_fritz'), 'Unbeteiligte: kein Fehler …');
select pg_temp.als('anna');
select ok(pg_temp.ausgehend('anna', 'f***@example.org') is not null, '… und die Anfrage bleibt');
select lives_ok(format('select public.cancel_friend_request(%L)', :'an_fritz'), 'Anna zieht ihre Anfrage zurück');
select ok(pg_temp.ausgehend('anna', 'f***@example.org') is null, 'Zurückziehen: die Anfrage ist gelöscht');
select lives_ok(format('select public.cancel_friend_request(%L)', :'an_fritz'), 'Nochmals zurückziehen: kein Fehler');
select lives_ok(format('select public.cancel_friend_request(%L)', pg_temp.ausgehend('anna', 'n***@example.org')),
  'Auch eine Anfrage ohne Empfänger lässt sich zurückziehen');
select ok(pg_temp.ausgehend('anna', 'n***@example.org') is null, '… sie ist danach gelöscht');
select pg_temp.als('fritz');
select is_empty($$ select 1 from public.friend_requests $$, 'Fritz sieht die zurückgezogene Anfrage nicht mehr');

-- ---------------------------------------------------------------------------------------------------
-- remove_friend: löscht nur die eigene Freundschaft, idempotent
-- ---------------------------------------------------------------------------------------------------
select pg_temp.als('anna');
select lives_ok(format('select public.remove_friend(%L)', pg_temp.id('dora')),
  'Anna „entfreundet“ Dora, mit der sie gar nicht befreundet ist');
select lives_ok(format('select public.remove_friend(%L)', gen_random_uuid()),
  'Unbekannte Person entfernen: kein Fehler');
select ok(pg_temp.befreundet('clara', 'dora'), 'Fremde Freundschaften (Clara ↔ Dora) bleiben unberührt');
select pg_temp.als('ben');
select lives_ok(format('select public.remove_friend(%L)', pg_temp.id('anna')), 'Ben beendet die Freundschaft mit Anna');
select ok(not pg_temp.befreundet('anna', 'ben'), 'Die Freundschaft ist gelöscht');
select lives_ok(format('select public.remove_friend(%L)', pg_temp.id('anna')), 'Nochmals entfernen ist kein Fehler (idempotent)');

-- ---------------------------------------------------------------------------------------------------
-- Limit: 20 Anfragen pro Nutzer und Tag (unverändert)
-- ---------------------------------------------------------------------------------------------------
select pg_temp.als('fritz');
select lives_ok(
  $$ select public.send_friend_request('unbekannt' || n || '@example.org') from generate_series(1, 19) n $$,
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
select throws_ok($$ select * from public.list_outgoing_friend_requests() $$, '42501', null,
  'anon darf list_outgoing_friend_requests nicht ausführen');
select throws_ok(format('select public.cancel_friend_request(%L)', gen_random_uuid()), '42501', null,
  'anon darf cancel_friend_request nicht ausführen');
select pg_temp.als_postgres();
set local role authenticated;   -- Rolle authenticated, aber ohne sub-Claim
select throws_ok($$ select public.send_friend_request('ben@example.org') $$, 'P0001', 'not_authenticated',
  'Ohne Nutzer-ID im JWT: not_authenticated');
select throws_ok($$ select * from public.list_outgoing_friend_requests() $$, 'P0001', 'not_authenticated',
  'Ohne Nutzer-ID im JWT: list_outgoing_friend_requests → not_authenticated');
select throws_ok(format('select public.cancel_friend_request(%L)', gen_random_uuid()), 'P0001', 'not_authenticated',
  'Ohne Nutzer-ID im JWT: cancel_friend_request → not_authenticated');

select * from finish();
rollback;
