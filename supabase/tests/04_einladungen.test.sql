-- pgTAP: Einladungs-Links – Token-Format, Ablauf nach 14 Tagen, ein neuer Link ersetzt den alten,
-- eigener Link, Freundschaft sofort, Limit 30 pro Tag, kein direkter Client-Zugriff auf invite_links.
-- Wer entfernt wurde, kann sich mit einem alten Link nicht selbst wieder befreunden.
begin;
select plan(41);

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
create function pg_temp.befreundet(a text, b text) returns boolean language sql security definer as $$
  select exists (select 1 from public.friendships f
                 where f.user_a = least(pg_temp.id(a), pg_temp.id(b))
                   and f.user_b = greatest(pg_temp.id(a), pg_temp.id(b)))
$$;
create function pg_temp.entfernt(von text, wen text) returns boolean language sql security definer as $$
  select exists (select 1 from private.friend_removals e
                 where e.remover = pg_temp.id(von) and e.removed = pg_temp.id(wen))
$$;
grant execute on all functions in schema pg_temp to public;

select pg_temp.nutzer('anna');
select pg_temp.nutzer('ben');
select pg_temp.nutzer('clara');
select pg_temp.nutzer('dora');
select pg_temp.nutzer('emil');

-- ---------------------------------------------------------------------------------------------------
-- create_invite_link
-- ---------------------------------------------------------------------------------------------------
select pg_temp.als('anna');
select public.create_invite_link() as token_alt \gset
select matches(:'token_alt'::text, '^[A-Za-z0-9_-]{32}$', 'Token: 32 Zeichen base64url (192 bit ≥ 128 bit)');

select pg_temp.als_postgres();
select results_eq(
  $$ select created_at, expires_at from public.invite_links where owner = pg_temp.id('anna') $$,
  $$ values (now(), now() + interval '14 days') $$,
  'Der Link ist ab jetzt 14 Tage gültig'
);

select pg_temp.als('anna');
select throws_ok($$ select token from public.invite_links $$, '42501', null, 'Clients können invite_links nicht lesen');
select throws_ok(
  $$ insert into public.invite_links (token, owner) values ('AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA', pg_temp.id('anna')) $$,
  '42501', null, 'Clients können invite_links nicht direkt anlegen'
);
select throws_ok($$ delete from public.invite_links $$, '42501', null, 'Clients können invite_links nicht direkt löschen');

-- Neuer Link ersetzt den alten
select public.create_invite_link() as token \gset
select isnt(:'token'::text, :'token_alt'::text, 'Ein neuer Link hat ein neues Token');
select pg_temp.als_postgres();
select is((select count(*)::int from public.invite_links where owner = pg_temp.id('anna')), 1,
  'Pro Nutzer höchstens ein Link');
select pg_temp.als('ben');
select throws_ok(format('select public.accept_invite(%L)', :'token_alt'), 'P0001', 'invite_invalid',
  'Der alte Link ist verfallen');

-- ---------------------------------------------------------------------------------------------------
-- accept_invite
-- ---------------------------------------------------------------------------------------------------
select pg_temp.als('anna');
select throws_ok(format('select public.accept_invite(%L)', :'token'), 'P0001', 'invite_own',
  'Der eigene Link kann nicht angenommen werden');
select ok(not pg_temp.befreundet('anna', 'anna'), 'Keine Freundschaft mit sich selbst');

select pg_temp.als('ben');
select results_eq(
  format('select id, display_name from public.accept_invite(%L)', :'token'),
  $$ values (pg_temp.id('anna'), 'Anna'::text) $$,
  'Ben nimmt Annas Einladung an und bekommt Annas Profil zurück'
);
select ok(pg_temp.befreundet('anna', 'ben'), 'Einladung angenommen: sofort befreundet');
select set_eq($$ select id from public.profiles $$, array[pg_temp.id('ben'), pg_temp.id('anna')],
  'Ben sieht jetzt Annas Profil');
select results_eq(
  format('select id from public.accept_invite(%L)', :'token'),
  $$ values (pg_temp.id('anna')) $$,
  'Nochmals annehmen (schon befreundet): kein Fehler, wieder Annas Profil'
);
select pg_temp.als_postgres();
select is((select count(*)::int from public.friendships), 1, 'Weiterhin genau eine Freundschaft');

-- Offene Anfragen zwischen beiden werden mit aufgeräumt; der Link gilt für mehrere Personen.
select pg_temp.als('clara');
select public.send_friend_request('anna@example.org');
select lives_ok(format('select public.accept_invite(%L)', :'token'), 'Clara nimmt denselben Link an');
select ok(pg_temp.befreundet('anna', 'clara'), 'Auch Clara ist jetzt mit Anna befreundet');
select pg_temp.als_postgres();
select is_empty($$ select 1 from public.friend_requests $$, 'Die offene Anfrage Clara → Anna ist erledigt');

-- ---------------------------------------------------------------------------------------------------
-- Entfernen: Der alte Link bringt die entfernte Person nicht zurück (Review 3, Befund 8)
-- ---------------------------------------------------------------------------------------------------
select pg_temp.als('anna');
select lives_ok(format('select public.remove_friend(%L)', pg_temp.id('ben')), 'Anna entfernt Ben');
select ok(pg_temp.entfernt('anna', 'ben'), 'Das Entfernen wird vermerkt');
select pg_temp.als('ben');
select throws_ok(format('select public.accept_invite(%L)', :'token'), 'P0001', 'invite_invalid',
  'Ben kann sich mit Annas altem Link nicht selbst wieder befreunden');
select ok(not pg_temp.befreundet('anna', 'ben'), 'Anna und Ben bleiben getrennt');
select pg_temp.als('emil');
select lives_ok(format('select public.accept_invite(%L)', :'token'), 'Für alle anderen gilt Annas Link weiter');
select ok(pg_temp.befreundet('anna', 'emil'), 'Emil ist mit Anna befreundet');

-- Wer selbst entfernt hat, darf den Link der anderen Person weiter nutzen (deren Zustimmung gilt noch).
select pg_temp.als('ben');
select public.create_invite_link() as token_ben_frueh \gset
select pg_temp.als('anna');
select lives_ok(format('select public.accept_invite(%L)', :'token_ben_frueh'),
  'Anna nimmt Bens Link an, nachdem sie selbst Ben entfernt hatte');
select ok(pg_temp.befreundet('anna', 'ben') and not pg_temp.entfernt('anna', 'ben'),
  'Wieder befreundet: der Vermerk über das Entfernen ist gelöscht');

-- Ein NACH dem Entfernen erzeugter Link ist eine neue Zustimmung.
select public.remove_friend(pg_temp.id('ben'));
select pg_temp.als_postgres();
update private.friend_removals set removed_at = now() - interval '1 minute'
 where remover = pg_temp.id('anna') and removed = pg_temp.id('ben');   -- „vor einer Minute entfernt“
select pg_temp.als('anna');
select public.create_invite_link() as token \gset
select pg_temp.als('ben');
select lives_ok(format('select public.accept_invite(%L)', :'token'), 'Ben nimmt Annas NEUEN Link an');
select ok(pg_temp.befreundet('anna', 'ben'), 'Mit einem neuen Link sind beide wieder befreundet');

-- Ohne bestehende Freundschaft wird nichts vermerkt.
select pg_temp.als('anna');
select lives_ok(format('select public.remove_friend(%L)', pg_temp.id('dora')), 'Anna entfernt Dora (keine Freundin)');
select ok(not pg_temp.entfernt('anna', 'dora'), 'Ohne Freundschaft kein Vermerk');

-- Unbekannte und ungültige Tokens
select pg_temp.als('dora');
select throws_ok($$ select public.accept_invite('AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA') $$, 'P0001', 'invite_invalid',
  'Unbekanntes Token: invite_invalid');
select throws_ok($$ select public.accept_invite('') $$, 'P0001', 'invite_invalid', 'Leeres Token: invite_invalid');
select throws_ok($$ select public.accept_invite(null) $$, 'P0001', 'invite_invalid', 'NULL: invite_invalid');

-- Ablauf nach 14 Tagen
select pg_temp.als_postgres();
update public.invite_links
   set created_at = now() - interval '14 days', expires_at = now()
 where owner = pg_temp.id('anna');
select pg_temp.als('dora');
select throws_ok(format('select public.accept_invite(%L)', :'token'), 'P0001', 'invite_invalid',
  'Nach 14 Tagen ist der Link abgelaufen');
select ok(not pg_temp.befreundet('anna', 'dora'), 'Abgelaufener Link: keine Freundschaft');

-- ---------------------------------------------------------------------------------------------------
-- Limit: 30 Annahmen pro Nutzer und Tag
-- ---------------------------------------------------------------------------------------------------
select pg_temp.als('ben');
select public.create_invite_link() as token_ben \gset
select pg_temp.als('dora');
select lives_ok(
  format('select public.accept_invite(%L) from generate_series(1, 30)', :'token_ben'),
  '30 Aufrufe an einem Tag sind erlaubt'
);
select throws_ok(format('select public.accept_invite(%L)', :'token_ben'), 'P0001', 'rate_limited',
  'Der 31. Aufruf am selben Tag: rate_limited');

-- ---------------------------------------------------------------------------------------------------
-- Ohne Anmeldung
-- ---------------------------------------------------------------------------------------------------
select pg_temp.als_gast();
select throws_ok($$ select public.create_invite_link() $$, '42501', null, 'anon darf keinen Link erzeugen');
select throws_ok(format('select public.accept_invite(%L)', :'token_ben'), '42501', null, 'anon darf keinen Link annehmen');
select throws_ok($$ select token from public.invite_links $$, '42501', null, 'anon kann invite_links nicht lesen');
select pg_temp.als_postgres();
set local role authenticated;   -- Rolle authenticated, aber ohne sub-Claim
select throws_ok($$ select public.create_invite_link() $$, 'P0001', 'not_authenticated',
  'Ohne Nutzer-ID im JWT: not_authenticated');

select * from finish();
rollback;
