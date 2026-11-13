-- pgTAP: Profil-Trigger auf auth.users und RLS-Matrix für public.profiles
-- (selbst / Freund / Absender einer eingehenden Anfrage / Empfänger einer ausgehenden Anfrage / Fremder /
-- nicht angemeldet). Der Absender einer Anfrage sieht das Profil des Empfängers NICHT – sonst ließe sich prüfen,
-- ob es zu einer E-Mail-Adresse ein Konto gibt.
begin;
select plan(33);

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
-- Offene Anfrage, wie send_friend_request sie anlegt (Kennung und Hinweis aus der Adresse des Empfängers).
create function pg_temp.anfrage_anlegen(von text, an text) returns uuid language sql as $$
  insert into public.friend_requests (from_user, to_user, email_hash, email_hint)
  values (pg_temp.id(von), pg_temp.id(an), private.friend_request_hash(pg_temp.id(von), an || '@example.org'),
          private.email_hint(an || '@example.org'))
  returning id
$$;
grant execute on all functions in schema pg_temp to public;

-- ---------------------------------------------------------------------------------------------------
-- Trigger: Profil aus den Google-Metadaten
-- ---------------------------------------------------------------------------------------------------
select pg_temp.nutzer('anna', '{"full_name": "Anna Beispiel", "avatar_url": "https://example.org/anna.png"}');
select pg_temp.nutzer('ben', '{"name": "Ben", "picture": "https://example.org/ben.png"}');
select pg_temp.nutzer('clara', '{}');
select pg_temp.nutzer('dora', jsonb_build_object('full_name', E'  Dora \n\t Lang  ', 'avatar_url', 'http://unsicher.example/d.png'));
select pg_temp.nutzer('emil', jsonb_build_object('full_name', repeat('x', 60), 'avatar_url', 'https://example.org/' || repeat('a', 2100)));
insert into auth.users (id, email) values (pg_temp.id('fritz'), 'fritz@example.org');  -- ganz ohne Metadaten

select is(
  (select row(display_name, avatar_url)::text from public.profiles where id = pg_temp.id('anna')),
  row('Anna Beispiel', 'https://example.org/anna.png')::text,
  'Trigger: Name aus full_name, Bild aus avatar_url'
);
select is(
  (select row(display_name, avatar_url)::text from public.profiles where id = pg_temp.id('ben')),
  row('Ben', 'https://example.org/ben.png')::text,
  'Trigger: Name aus name, Bild aus picture'
);
select is(
  (select row(display_name, avatar_url)::text from public.profiles where id = pg_temp.id('clara')),
  row('Drink-With-Me-Nutzer', null::text)::text,
  'Trigger: ohne Namen ein neutraler Name (nie die E-Mail-Adresse), kein Bild'
);
select is(
  (select row(display_name, avatar_url)::text from public.profiles where id = pg_temp.id('dora')),
  row('Dora Lang', null::text)::text,
  'Trigger: Leer- und Steuerzeichen bereinigt, Bild ohne https verworfen'
);
select is(
  (select row(display_name, avatar_url)::text from public.profiles where id = pg_temp.id('emil')),
  row(repeat('x', 50), null::text)::text,
  'Trigger: Name auf 50 Zeichen gekürzt, zu lange Bild-URL verworfen'
);
select is(
  (select display_name from public.profiles where id = pg_temp.id('fritz')),
  'Drink-With-Me-Nutzer',
  'Trigger: auch ohne raw_user_meta_data entsteht ein Profil'
);
select is(
  (select count(*)::int from public.profiles),
  6,
  'Zu jedem Auth-Nutzer genau ein Profil'
);

-- ---------------------------------------------------------------------------------------------------
-- Ausgangslage für die RLS-Matrix:
--   Anna ↔ Ben befreundet · Clara → Anna offene Anfrage · Anna → Emil offene Anfrage · Dora fremd
-- ---------------------------------------------------------------------------------------------------
select pg_temp.befreunden('anna', 'ben');
select pg_temp.anfrage_anlegen('clara', 'anna');
select pg_temp.anfrage_anlegen('anna', 'emil');

select pg_temp.als('anna');
select set_eq(
  'select id from public.profiles',
  array[pg_temp.id('anna'), pg_temp.id('ben'), pg_temp.id('clara')],
  'Anna sieht sich, ihren Freund Ben und Clara (Absenderin einer eingehenden Anfrage)'
);
select is_empty(
  $$ select 1 from public.profiles where id = pg_temp.id('emil') $$,
  'Anna sieht Emil NICHT, obwohl sie ihm eine Anfrage geschickt hat (keine Bestätigung, dass es das Konto gibt)'
);
select pg_temp.als('ben');
select set_eq(
  'select id from public.profiles',
  array[pg_temp.id('ben'), pg_temp.id('anna')],
  'Ben sieht sich und seine Freundin Anna, aber nicht deren Anfrage-Kontakte'
);
select pg_temp.als('clara');
select set_eq(
  'select id from public.profiles',
  array[pg_temp.id('clara')],
  'Clara (Absenderin einer Anfrage) sieht nur sich – Annas Profil erst nach der Annahme'
);
select pg_temp.als('emil');
select set_eq(
  'select id from public.profiles',
  array[pg_temp.id('emil'), pg_temp.id('anna')],
  'Emil (Empfänger einer Anfrage) sieht sich und Anna (Name der Absenderin)'
);
select pg_temp.als('dora');
select set_eq(
  'select id from public.profiles',
  array[pg_temp.id('dora')],
  'Dora (Fremde) sieht nur sich selbst'
);
select pg_temp.als_gast();
select throws_ok('select id from public.profiles', '42501', null, 'Nicht angemeldet: kein Lesezugriff auf Profile');

-- ---------------------------------------------------------------------------------------------------
-- Ändern: nur das eigene Profil, nur display_name und avatar_url
-- ---------------------------------------------------------------------------------------------------
select pg_temp.als('anna');
select lives_ok(
  $$ update public.profiles set display_name = 'Anna B.', avatar_url = 'https://example.org/neu.png'
     where id = pg_temp.id('anna') $$,
  'Anna ändert ihren Namen und ihr Bild'
);
select is(
  (select display_name from public.profiles where id = pg_temp.id('anna')),
  'Anna B.',
  'Die Änderung ist gespeichert'
);
select is_empty(
  $$ update public.profiles set display_name = 'Gehackt' where id = pg_temp.id('ben') returning id $$,
  'Anna kann Bens Profil nicht ändern (RLS)'
);
select is_empty(
  $$ update public.profiles set display_name = 'Gehackt' where id = pg_temp.id('dora') returning id $$,
  'Anna kann das Profil einer Fremden nicht ändern (RLS)'
);
select throws_ok(
  $$ update public.profiles set id = pg_temp.id('dora') where id = pg_temp.id('anna') $$,
  '42501', null, 'Die ID ist nicht änderbar'
);
select throws_ok(
  $$ update public.profiles set created_at = now() - interval '1 year' where id = pg_temp.id('anna') $$,
  '42501', null, 'created_at ist nicht änderbar'
);
select throws_ok(
  $$ update public.profiles set display_name = '   ' where id = pg_temp.id('anna') $$,
  '23514', null, 'Leerer Anzeigename wird abgelehnt'
);
select throws_ok(
  $$ update public.profiles set display_name = repeat('y', 51) where id = pg_temp.id('anna') $$,
  '23514', null, 'Anzeigename über 50 Zeichen wird abgelehnt'
);
select throws_ok(
  $$ update public.profiles set display_name = E'Anna\nB.' where id = pg_temp.id('anna') $$,
  '23514', null, 'Steuerzeichen im Anzeigenamen werden abgelehnt'
);
select throws_ok(
  $$ update public.profiles set avatar_url = 'javascript:alert(1)' where id = pg_temp.id('anna') $$,
  '23514', null, 'Bild-URL muss https sein'
);
select throws_ok(
  $$ update public.profiles set avatar_url = 'https://example.org/' || repeat('z', 2040) where id = pg_temp.id('anna') $$,
  '23514', null, 'Bild-URL über 2048 Zeichen wird abgelehnt'
);
select lives_ok(
  $$ update public.profiles set avatar_url = null where id = pg_temp.id('anna') $$,
  'Bild entfernen ist erlaubt'
);
select throws_ok(
  $$ insert into public.profiles (id, display_name) values (gen_random_uuid(), 'Neu') $$,
  '42501', null, 'Profile können nicht direkt angelegt werden'
);
select throws_ok(
  $$ delete from public.profiles where id = pg_temp.id('anna') $$,
  '42501', null, 'Profile können nicht direkt gelöscht werden'
);

select pg_temp.als_gast();
select throws_ok(
  $$ update public.profiles set display_name = 'Gast' $$,
  '42501', null, 'Nicht angemeldet: keine Änderungen'
);

select pg_temp.als_postgres();
select is(
  (select display_name from public.profiles where id = pg_temp.id('ben')),
  'Ben',
  'Bens Profil ist unverändert'
);
select is(
  (select display_name from public.profiles where id = pg_temp.id('dora')),
  'Dora Lang',
  'Doras Profil ist unverändert'
);

-- Hilfsfunktionen direkt
select is(private.display_name_from_metadata('{"full_name": "", "name": "Zweitname"}'), 'Zweitname',
  'Leerer full_name fällt auf name zurück');
select is(private.avatar_url_from_metadata('{"avatar_url": "ftp://x", "picture": "https://example.org/p.png"}'),
  'https://example.org/p.png', 'Ungültige avatar_url fällt auf picture zurück');

select * from finish();
rollback;
