-- pgTAP: Geteilte Trink-Ereignisse (drink_events) – Anlegen nur als man selbst und nur „jetzt“
-- (letzte 24 h bis +5 min), Lesen für Autor und Freunde, Löschen nur durch den Autor, KEIN UPDATE.
begin;
select plan(35);

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
-- Feste Ereignis-IDs (in der App: lokale DrinkEntry.id)
create function pg_temp.ereignis(nr int) returns uuid language sql immutable as $$
  select ('00000000-0000-4000-8000-' || lpad(nr::text, 12, '0'))::uuid
$$;
grant execute on all functions in schema pg_temp to public;

select pg_temp.nutzer('anna');
select pg_temp.nutzer('ben');
select pg_temp.nutzer('clara');
select pg_temp.befreunden('anna', 'ben');

-- ---------------------------------------------------------------------------------------------------
-- INSERT: nur als man selbst, nur „jetzt“
-- ---------------------------------------------------------------------------------------------------
select pg_temp.als('anna');
select lives_ok(
  $$ insert into public.drink_events (id, name, icon, category, volume_ml, consumed_at)
     values (pg_temp.ereignis(1), 'Apfelschorle', 'apple_spritzer', 'JUICE', 500, now()) $$,
  'Anna teilt eine Apfelschorle (Zeitpunkt jetzt)'
);
select results_eq(
  $$ select author_id, notified_at, created_at from public.drink_events where id = pg_temp.ereignis(1) $$,
  $$ values (pg_temp.id('anna'), null::timestamptz, now()) $$,
  'author_id = auth.uid(), created_at = jetzt, notified_at leer'
);
select lives_ok(
  $$ insert into public.drink_events (id, author_id, name, icon, category, volume_ml, consumed_at)
     values (pg_temp.ereignis(2), pg_temp.id('anna'), 'Kaffee', 'coffee', 'COFFEE', 200,
             now() - interval '23 hours 59 minutes') $$,
  'Nachträglich synchronisiert: bis zu 24 Stunden alt ist erlaubt'
);
select lives_ok(
  $$ insert into public.drink_events (id, name, icon, category, volume_ml, consumed_at)
     values (pg_temp.ereignis(3), 'Wasser', 'water', 'WATER', 250, now() + interval '4 minutes') $$,
  'Leicht vorgehende Uhr: bis zu 5 Minuten in der Zukunft ist erlaubt'
);
select throws_ok(
  $$ insert into public.drink_events (id, name, icon, category, volume_ml, consumed_at)
     values (pg_temp.ereignis(4), 'Bier', 'beer', 'BEER', 500, now() - interval '24 hours 1 second') $$,
  '42501', null, 'Älter als 24 Stunden wird abgelehnt (Zeitpunkt immer „jetzt“)'
);
select throws_ok(
  $$ insert into public.drink_events (id, name, icon, category, volume_ml, consumed_at)
     values (pg_temp.ereignis(4), 'Bier', 'beer', 'BEER', 500, now() + interval '5 minutes 1 second') $$,
  '42501', null, 'Mehr als 5 Minuten in der Zukunft wird abgelehnt'
);
select throws_ok(
  $$ insert into public.drink_events (id, author_id, name, icon, category, volume_ml, consumed_at)
     values (pg_temp.ereignis(4), pg_temp.id('ben'), 'Bier', 'beer', 'BEER', 500, now()) $$,
  '42501', null, 'Ereignisse im Namen anderer werden abgelehnt'
);
select throws_ok(
  $$ insert into public.drink_events (id, name, icon, category, volume_ml, consumed_at, notified_at)
     values (pg_temp.ereignis(4), 'Bier', 'beer', 'BEER', 500, now(), now()) $$,
  '42501', null, 'notified_at kann der Client nicht setzen'
);
select throws_ok(
  $$ insert into public.drink_events (id, name, icon, category, volume_ml, consumed_at, created_at)
     values (pg_temp.ereignis(4), 'Bier', 'beer', 'BEER', 500, now(), now() - interval '1 hour') $$,
  '42501', null, 'created_at kann der Client nicht setzen (Schutz der 10-Minuten-Grenze)'
);
select throws_ok(
  $$ insert into public.drink_events (id, name, icon, category, volume_ml, consumed_at)
     values (pg_temp.ereignis(4), 'Bier', 'Bier!', 'BEER', 500, now()) $$,
  '23514', null, 'Icon muss ^[a-z_]{1,30}$ entsprechen'
);
select throws_ok(
  $$ insert into public.drink_events (id, name, icon, category, volume_ml, consumed_at)
     values (pg_temp.ereignis(4), 'Bier', 'beer', 'BIER', 500, now()) $$,
  '23514', null, 'Kategorie muss ein DrinkCategory-Name sein'
);
select throws_ok(
  $$ insert into public.drink_events (id, name, icon, category, volume_ml, consumed_at)
     values (pg_temp.ereignis(4), 'Bier', 'beer', 'BEER', 0, now()) $$,
  '23514', null, 'Menge mindestens 1 ml'
);
select throws_ok(
  $$ insert into public.drink_events (id, name, icon, category, volume_ml, consumed_at)
     values (pg_temp.ereignis(4), 'Bier', 'beer', 'BEER', 2001, now()) $$,
  '23514', null, 'Menge höchstens 2000 ml'
);
select throws_ok(
  $$ insert into public.drink_events (id, name, icon, category, volume_ml, consumed_at)
     values (pg_temp.ereignis(4), repeat('B', 41), 'beer', 'BEER', 500, now()) $$,
  '23514', null, 'Name höchstens 40 Zeichen'
);
select throws_ok(
  $$ insert into public.drink_events (id, name, icon, category, volume_ml, consumed_at)
     values (pg_temp.ereignis(4), '', 'beer', 'BEER', 500, now()) $$,
  '23514', null, 'Name mindestens 1 Zeichen'
);
select throws_ok(
  $$ insert into public.drink_events (id, name, icon, category, volume_ml, consumed_at)
     values (pg_temp.ereignis(1), 'Doppelt', 'water', 'WATER', 250, now()) $$,
  '23505', null, 'Dieselbe Ereignis-ID nur einmal'
);
select lives_ok(
  $$ insert into public.drink_events (id, name, icon, category, volume_ml, consumed_at)
     values (pg_temp.ereignis(1), 'Doppelt', 'water', 'WATER', 250, now()) on conflict do nothing $$,
  'Erneutes Senden mit ON CONFLICT DO NOTHING ist idempotent'
);
select lives_ok(
  $$ insert into public.drink_events (id, name, icon, category, volume_ml, consumed_at)
     select pg_temp.ereignis(10 + k::int), 'Kategorie ' || k, 'other', c, 100, now()
       from unnest(array['WATER', 'COFFEE', 'TEA', 'JUICE', 'SOFT_DRINK', 'ENERGY_DRINK', 'MILK', 'BEER',
                         'WINE', 'SPARKLING_WINE', 'SPIRIT', 'COCKTAIL', 'OTHER']) with ordinality as t(c, k) $$,
  'Alle 13 DrinkCategory-Namen sind gültig'
);

select pg_temp.als('clara');
select lives_ok(
  $$ insert into public.drink_events (id, name, icon, category, volume_ml, consumed_at)
     values (pg_temp.ereignis(100), 'Tee', 'tea', 'TEA', 250, now()) $$,
  'Clara teilt einen Tee'
);

-- ---------------------------------------------------------------------------------------------------
-- SELECT: Autor und Freunde des Autors
-- ---------------------------------------------------------------------------------------------------
select pg_temp.als('anna');
select is((select count(*)::int from public.drink_events), 16, 'Anna sieht ihre eigenen 16 Ereignisse');
select pg_temp.als('ben');
select is((select count(*)::int from public.drink_events where author_id = pg_temp.id('anna')), 16,
  'Ben (Freund) sieht Annas Ereignisse');
select is_empty($$ select 1 from public.drink_events where author_id = pg_temp.id('clara') $$,
  'Ben sieht Claras Ereignisse nicht (nicht befreundet)');
select pg_temp.als('clara');
select set_eq($$ select id from public.drink_events $$, array[pg_temp.ereignis(100)],
  'Clara (Fremde) sieht nur ihr eigenes Ereignis');
select pg_temp.als_gast();
select throws_ok($$ select 1 from public.drink_events $$, '42501', null, 'Nicht angemeldet: kein Zugriff');
select throws_ok(
  $$ insert into public.drink_events (id, author_id, name, icon, category, volume_ml, consumed_at)
     values (gen_random_uuid(), pg_temp.id('anna'), 'Gast', 'water', 'WATER', 250, now()) $$,
  '42501', null, 'Nicht angemeldet: kein Anlegen'
);

-- ---------------------------------------------------------------------------------------------------
-- Kein UPDATE (auch nicht durch den Autor)
-- ---------------------------------------------------------------------------------------------------
select pg_temp.als('anna');
select throws_ok($$ update public.drink_events set name = 'Wasser' where id = pg_temp.ereignis(1) $$,
  '42501', null, 'Autor kann ein Ereignis nicht ändern');
select throws_ok($$ update public.drink_events set notified_at = null where id = pg_temp.ereignis(1) $$,
  '42501', null, 'Autor kann notified_at nicht zurücksetzen (keine Mehrfach-Benachrichtigung)');
select pg_temp.als('ben');
select throws_ok($$ update public.drink_events set volume_ml = 1 $$, '42501', null, 'Freunde können nichts ändern');

-- ---------------------------------------------------------------------------------------------------
-- DELETE: nur der Autor
-- ---------------------------------------------------------------------------------------------------
select pg_temp.als('ben');
select is_empty($$ delete from public.drink_events where id = pg_temp.ereignis(1) returning id $$,
  'Freund Ben kann Annas Ereignis nicht löschen');
select pg_temp.als('clara');
select is_empty($$ delete from public.drink_events where author_id = pg_temp.id('anna') returning id $$,
  'Fremde können Annas Ereignisse nicht löschen');
select pg_temp.als('anna');
select results_eq($$ delete from public.drink_events where id = pg_temp.ereignis(1) returning id $$,
  $$ values (pg_temp.ereignis(1)) $$, 'Anna zieht ihr Ereignis zurück (unpublish)');
select is((select count(*)::int from public.drink_events), 15, 'Annas übrige Ereignisse bleiben');

-- Nach dem Entfreunden sieht Ben nichts mehr
select public.remove_friend(pg_temp.id('ben'));
select pg_temp.als('ben');
select is_empty($$ select 1 from public.drink_events where author_id = pg_temp.id('anna') $$,
  'Nach dem Entfreunden sieht Ben Annas Ereignisse nicht mehr');

-- ---------------------------------------------------------------------------------------------------
-- service_role (Edge Function notify-friends) darf notified_at setzen
-- ---------------------------------------------------------------------------------------------------
select pg_temp.als_server();
select results_eq(
  $$ update public.drink_events set notified_at = now()
      where id = pg_temp.ereignis(2) and author_id = pg_temp.id('anna') and notified_at is null
     returning id $$,
  $$ values (pg_temp.ereignis(2)) $$,
  'service_role setzt notified_at genau einmal …'
);
select is_empty(
  $$ update public.drink_events set notified_at = now()
      where id = pg_temp.ereignis(2) and author_id = pg_temp.id('anna') and notified_at is null
     returning id $$,
  '… ein zweiter Versuch findet nichts mehr (notified_at-Sperre)'
);

select * from finish();
rollback;
