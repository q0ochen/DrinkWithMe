-- pgTAP: Push-Abos (push_subscriptions) – nur der Besitzer liest/legt an/löscht, kein UPDATE,
-- höchstens 5 Abos pro Nutzer (Trigger), Format-Prüfungen; die Service-Rolle liest alle (Versand).
begin;
select plan(25);

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
-- SQL für ein gültiges Abo des angemeldeten Nutzers (Schlüssel im base64url-Format ohne Auffüllung).
create function pg_temp.abo_sql(nr int, plattform text default 'unifiedpush') returns text language sql immutable as $$
  select format(
    'insert into public.push_subscriptions (endpoint, p256dh, auth, platform) values (%L, %L, %L, %L)',
    'https://ntfy.example.org/upABC' || nr, 'B' || repeat('A', 86), repeat('b', 22), plattform)
$$;
grant execute on all functions in schema pg_temp to public;

select pg_temp.nutzer('anna');
select pg_temp.nutzer('ben');
select pg_temp.nutzer('clara');

-- ---------------------------------------------------------------------------------------------------
-- Anlegen
-- ---------------------------------------------------------------------------------------------------
select pg_temp.als('anna');
select lives_ok(pg_temp.abo_sql(1), 'Anna legt ein UnifiedPush-Abo an');
select is((select user_id from public.push_subscriptions where endpoint = 'https://ntfy.example.org/upABC1'),
  pg_temp.id('anna'), 'user_id = auth.uid() als Vorgabe');
select lives_ok(pg_temp.abo_sql(2, 'web'), 'Anna legt ein Web-Push-Abo an');
select throws_ok(
  $$ insert into public.push_subscriptions (user_id, endpoint, p256dh, auth, platform)
     values (pg_temp.id('ben'), 'https://ntfy.example.org/fremd', 'B' || repeat('A', 86), repeat('b', 22), 'web') $$,
  '42501', null, 'Abos für andere Nutzer werden abgelehnt'
);
select throws_ok(pg_temp.abo_sql(1), '23505', null, 'Derselbe Endpoint nur einmal');
select throws_ok(
  $$ insert into public.push_subscriptions (endpoint, p256dh, auth, platform)
     values ('http://ntfy.example.org/unsicher', 'B' || repeat('A', 86), repeat('b', 22), 'web') $$,
  '23514', null, 'Endpoint muss https sein'
);
select throws_ok(
  $$ insert into public.push_subscriptions (endpoint, p256dh, auth, platform)
     values ('https://ntfy.example.org/x', 'B' || repeat('A', 86), repeat('b', 22), 'fcm') $$,
  '23514', null, 'Nur die Plattformen unifiedpush und web'
);
select throws_ok(
  $$ insert into public.push_subscriptions (endpoint, p256dh, auth, platform)
     values ('https://ntfy.example.org/x', 'zu kurz', repeat('b', 22), 'web') $$,
  '23514', null, 'p256dh muss ein P-256-Schlüssel sein'
);
select throws_ok(
  $$ insert into public.push_subscriptions (endpoint, p256dh, auth, platform)
     values ('https://ntfy.example.org/x', 'B' || repeat('A', 86), 'kurz', 'web') $$,
  '23514', null, 'auth muss ein 16-Byte-Geheimnis sein'
);
select throws_ok(
  $$ insert into public.push_subscriptions (endpoint, p256dh, auth, platform)
     values ('https://ntfy.example.org/' || repeat('x', 2048), 'B' || repeat('A', 86), repeat('b', 22), 'web') $$,
  '23514', null, 'Endpoint höchstens 2048 Zeichen'
);
select throws_ok(
  $$ insert into public.push_subscriptions (endpoint, p256dh, auth, platform, updated_at)
     values ('https://ntfy.example.org/y', 'B' || repeat('A', 86), repeat('b', 22), 'web', now()) $$,
  '42501', null, 'Zeitstempel setzt nur der Server'
);

-- ---------------------------------------------------------------------------------------------------
-- Höchstens 5 Abos pro Nutzer
-- ---------------------------------------------------------------------------------------------------
select lives_ok(pg_temp.abo_sql(3), 'Abo 3');
select lives_ok(pg_temp.abo_sql(4), 'Abo 4');
select lives_ok(pg_temp.abo_sql(5), 'Abo 5');
select throws_ok(pg_temp.abo_sql(6), 'P0001', 'too_many_subscriptions', 'Ein sechstes Abo wird abgelehnt');

select pg_temp.als('ben');
select lives_ok(pg_temp.abo_sql(7), 'Das Limit gilt pro Nutzer: Ben kann trotzdem ein Abo anlegen');

-- ---------------------------------------------------------------------------------------------------
-- Lesen, Ändern, Löschen
-- ---------------------------------------------------------------------------------------------------
select pg_temp.als('ben');
select results_eq($$ select endpoint from public.push_subscriptions $$,
  $$ values ('https://ntfy.example.org/upABC7'::text) $$, 'Ben sieht nur sein eigenes Abo');
select pg_temp.als('clara');
select is_empty($$ select 1 from public.push_subscriptions $$, 'Clara sieht keine fremden Abos');
select is_empty($$ delete from public.push_subscriptions returning id $$, 'Clara kann keine fremden Abos löschen');
select pg_temp.als('anna');
select throws_ok($$ update public.push_subscriptions set platform = 'web' $$, '42501', null,
  'Abos lassen sich nicht ändern (nur löschen und neu anlegen)');
select results_eq(
  $$ delete from public.push_subscriptions where endpoint = 'https://ntfy.example.org/upABC5' returning endpoint $$,
  $$ values ('https://ntfy.example.org/upABC5'::text) $$, 'Anna löscht ein eigenes Abo');
select lives_ok(pg_temp.abo_sql(6), 'Danach ist wieder Platz für ein neues Abo');
select pg_temp.als_gast();
select throws_ok($$ select 1 from public.push_subscriptions $$, '42501', null, 'Nicht angemeldet: kein Zugriff');

-- ---------------------------------------------------------------------------------------------------
-- Service-Rolle (Versand in notify-friends)
-- ---------------------------------------------------------------------------------------------------
select pg_temp.als_postgres();
update public.push_subscriptions set created_at = now() - interval '1 day', updated_at = now() - interval '1 day';
select pg_temp.als_server();
select is((select count(*)::int from public.push_subscriptions), 6, 'service_role liest alle Abos');
select results_eq(
  $$ update public.push_subscriptions set auth = repeat('c', 22)
      where endpoint = 'https://ntfy.example.org/upABC7' returning updated_at $$,
  $$ values (now()) $$, 'Eine Änderung durch den Server aktualisiert updated_at'
);

select * from finish();
rollback;
