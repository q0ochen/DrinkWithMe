-- pgTAP: Aufbau des Schemas, Row Level Security auf jeder Tabelle, Rechte von anon/authenticated/
-- service_role, Eigenschaften der RPCs (SECURITY DEFINER, search_path = '').
-- Ausführen: supabase test db
begin;
select plan(78);

-- ---------------------------------------------------------------------------------------------------
-- Tabellen und RLS
-- ---------------------------------------------------------------------------------------------------
select tables_are(
  'public',
  array['profiles', 'friendships', 'friend_requests', 'invite_links', 'drink_events',
        'push_subscriptions', 'rate_limits'],
  'Das Schema public enthält genau die Tabellen aus dem Vertrag'
);

select is(
  (select coalesce(array_agg(c.relname::text order by c.relname), '{}')
     from pg_catalog.pg_class c
    where c.relnamespace = 'public'::regnamespace
      and c.relkind in ('r', 'p')
      and not c.relrowsecurity),
  '{}'::text[],
  'Row Level Security ist auf JEDER Tabelle im Schema public aktiv'
);

select hasnt_column('public', 'profiles', 'email', 'profiles hat KEIN E-Mail-Feld');
select col_is_pk('public', 'friendships', array['user_a', 'user_b'], 'friendships: PK (user_a, user_b)');
select col_is_pk('public', 'rate_limits', array['user_id', 'action'], 'rate_limits: PK (user_id, action)');
select col_is_unique('public', 'friend_requests', array['from_user', 'to_user'], 'friend_requests: UNIQUE (from_user, to_user)');
select col_is_unique('public', 'friend_requests', array['from_user', 'email_hash'], 'friend_requests: UNIQUE (from_user, email_hash) – eine Zeile pro Absender und Adresse');
select col_is_null('public', 'friend_requests', 'to_user', 'friend_requests.to_user darf leer sein (Anfrage, die niemanden erreicht)');
select col_not_null('public', 'friend_requests', 'email_hash', 'friend_requests.email_hash ist Pflicht');
select col_not_null('public', 'friend_requests', 'email_hint', 'friend_requests.email_hint ist Pflicht');
select col_is_unique('public', 'push_subscriptions', array['endpoint'], 'push_subscriptions: endpoint ist eindeutig');
select col_default_is('public', 'drink_events', 'author_id', 'auth.uid()', 'drink_events.author_id = auth.uid() als Vorgabe');
select col_default_is('public', 'push_subscriptions', 'user_id', 'auth.uid()', 'push_subscriptions.user_id = auth.uid() als Vorgabe');

-- Fremdschlüssel mit ON DELETE CASCADE (Konto löschen räumt alles ab). Einzige Ausnahme: friend_requests.to_user
-- wird beim Löschen des Empfängers leer (SET NULL) – die Anfrage gehört dem Absender, und ihr Verschwinden würde
-- ihm verraten, dass hinter der Adresse ein Konto stand.
select is(
  (select coalesce(array_agg(format('%s.%s', c.conrelid::regclass, c.conname) order by c.conname), '{}')
     from pg_catalog.pg_constraint c
    where c.contype = 'f'
      and c.connamespace in ('public'::regnamespace, 'private'::regnamespace)
      and c.confdeltype <> 'c'),
  array['friend_requests.friend_requests_to_user_fkey'],
  'Alle Fremdschlüssel in public und private löschen kaskadierend (außer friend_requests.to_user)'
);
select is(
  (select c.confdeltype::text from pg_catalog.pg_constraint c
    where c.conrelid = 'public.friend_requests'::regclass and c.conname = 'friend_requests_to_user_fkey'),
  'n',
  'friend_requests.to_user: ON DELETE SET NULL'
);
select fk_ok('public', 'profiles', 'id', 'auth', 'users', 'id', 'profiles.id → auth.users');

-- ---------------------------------------------------------------------------------------------------
-- anon: keinerlei Tabellenrechte
-- ---------------------------------------------------------------------------------------------------
select table_privs_are('public', 'profiles', 'anon', '{}', 'anon: keine Rechte auf profiles');
select table_privs_are('public', 'friendships', 'anon', '{}', 'anon: keine Rechte auf friendships');
select table_privs_are('public', 'friend_requests', 'anon', '{}', 'anon: keine Rechte auf friend_requests');
select table_privs_are('public', 'invite_links', 'anon', '{}', 'anon: keine Rechte auf invite_links');
select table_privs_are('public', 'drink_events', 'anon', '{}', 'anon: keine Rechte auf drink_events');
select table_privs_are('public', 'push_subscriptions', 'anon', '{}', 'anon: keine Rechte auf push_subscriptions');
select table_privs_are('public', 'rate_limits', 'anon', '{}', 'anon: keine Rechte auf rate_limits');
select is(
  (select count(*)::int
     from information_schema.column_privileges p
    where p.table_schema = 'public' and p.grantee in ('anon', 'PUBLIC')),
  0,
  'anon/PUBLIC: auch keine Spaltenrechte in public'
);

-- ---------------------------------------------------------------------------------------------------
-- authenticated: genau die erlaubten Operationen
-- ---------------------------------------------------------------------------------------------------
select table_privs_are('public', 'profiles', 'authenticated', array['SELECT'], 'authenticated: profiles nur lesen (UPDATE nur spaltenweise)');
select column_privs_are('public', 'profiles', 'display_name', 'authenticated', array['SELECT', 'UPDATE'], 'authenticated: display_name änderbar');
select column_privs_are('public', 'profiles', 'avatar_url', 'authenticated', array['SELECT', 'UPDATE'], 'authenticated: avatar_url änderbar');
select column_privs_are('public', 'profiles', 'id', 'authenticated', array['SELECT'], 'authenticated: profiles.id nicht änderbar');
select column_privs_are('public', 'profiles', 'created_at', 'authenticated', array['SELECT'], 'authenticated: profiles.created_at nicht änderbar');
select table_privs_are('public', 'friendships', 'authenticated', array['SELECT'], 'authenticated: friendships nur lesen');
select table_privs_are('public', 'friend_requests', 'authenticated', '{}', 'authenticated: friend_requests ohne Tabellenrechte (Zurückziehen nur per RPC)');
select column_privs_are('public', 'friend_requests', 'from_user', 'authenticated', array['SELECT'], 'authenticated: friend_requests.from_user lesbar (Empfänger)');
select column_privs_are('public', 'friend_requests', 'to_user', 'authenticated', array['SELECT'], 'authenticated: friend_requests.to_user lesbar (Empfänger)');
select column_privs_are('public', 'friend_requests', 'email_hash', 'authenticated', '{}', 'authenticated: email_hash nicht lesbar');
select column_privs_are('public', 'friend_requests', 'email_hint', 'authenticated', '{}', 'authenticated: email_hint nur per RPC');
select column_privs_are('public', 'friend_requests', 'declined_at', 'authenticated', '{}', 'authenticated: declined_at nicht lesbar');
select table_privs_are('public', 'invite_links', 'authenticated', '{}', 'authenticated: kein Zugriff auf invite_links');
select table_privs_are('public', 'drink_events', 'authenticated', array['SELECT', 'DELETE'], 'authenticated: drink_events lesen/löschen, kein UPDATE');
select column_privs_are('public', 'drink_events', 'consumed_at', 'authenticated', array['SELECT', 'INSERT'], 'authenticated: consumed_at beim Anlegen');
select column_privs_are('public', 'drink_events', 'notified_at', 'authenticated', array['SELECT'], 'authenticated: notified_at setzt nur der Server');
select column_privs_are('public', 'drink_events', 'created_at', 'authenticated', array['SELECT'], 'authenticated: created_at setzt nur der Server');
select table_privs_are('public', 'push_subscriptions', 'authenticated', array['SELECT', 'DELETE'], 'authenticated: push_subscriptions lesen/löschen, kein UPDATE');
select column_privs_are('public', 'push_subscriptions', 'endpoint', 'authenticated', array['SELECT', 'INSERT'], 'authenticated: endpoint beim Anlegen');
select table_privs_are('public', 'rate_limits', 'authenticated', '{}', 'authenticated: kein Zugriff auf rate_limits');

-- service_role (nur Edge Functions) darf lesen und schreiben
select table_privs_are('public', 'drink_events', 'service_role', array['SELECT', 'INSERT', 'UPDATE', 'DELETE'], 'service_role: drink_events');
select table_privs_are('public', 'push_subscriptions', 'service_role', array['SELECT', 'INSERT', 'UPDATE', 'DELETE'], 'service_role: push_subscriptions');

-- ---------------------------------------------------------------------------------------------------
-- RPCs: SECURITY DEFINER, search_path = '', nur authenticated
-- ---------------------------------------------------------------------------------------------------
select is_definer('public', 'send_friend_request', array['text'], 'send_friend_request ist SECURITY DEFINER');
select is_definer('public', 'respond_friend_request', array['uuid', 'boolean'], 'respond_friend_request ist SECURITY DEFINER');
select is_definer('public', 'create_invite_link', array[]::text[], 'create_invite_link ist SECURITY DEFINER');
select is_definer('public', 'accept_invite', array['text'], 'accept_invite ist SECURITY DEFINER');
select is_definer('public', 'remove_friend', array['uuid'], 'remove_friend ist SECURITY DEFINER');
select is_definer('public', 'list_outgoing_friend_requests', array[]::text[], 'list_outgoing_friend_requests ist SECURITY DEFINER');
select is_definer('public', 'cancel_friend_request', array['uuid'], 'cancel_friend_request ist SECURITY DEFINER');
select is_definer('private', 'enforce_drink_event_limit', array[]::text[], 'enforce_drink_event_limit ist SECURITY DEFINER');
select function_returns('public', 'accept_invite', array['text'], 'profiles', 'accept_invite liefert ein Profil');
select function_returns('public', 'create_invite_link', array[]::text[], 'text', 'create_invite_link liefert das Token');

select is(
  (select coalesce(array_agg(format('%s.%s', n.nspname, p.proname) order by n.nspname, p.proname), '{}')
     from pg_catalog.pg_proc p
     join pg_catalog.pg_namespace n on n.oid = p.pronamespace
    where n.nspname in ('public', 'private')
      and not coalesce('search_path=""' = any (p.proconfig), false)),
  '{}'::text[],
  'Jede Funktion in public und private hat search_path = '''''
);

select function_privs_are('public', 'send_friend_request', array['text'], 'authenticated', array['EXECUTE'], 'authenticated darf send_friend_request');
select function_privs_are('public', 'respond_friend_request', array['uuid', 'boolean'], 'authenticated', array['EXECUTE'], 'authenticated darf respond_friend_request');
select function_privs_are('public', 'create_invite_link', array[]::text[], 'authenticated', array['EXECUTE'], 'authenticated darf create_invite_link');
select function_privs_are('public', 'accept_invite', array['text'], 'authenticated', array['EXECUTE'], 'authenticated darf accept_invite');
select function_privs_are('public', 'remove_friend', array['uuid'], 'authenticated', array['EXECUTE'], 'authenticated darf remove_friend');
select function_privs_are('public', 'list_outgoing_friend_requests', array[]::text[], 'authenticated', array['EXECUTE'], 'authenticated darf list_outgoing_friend_requests');
select function_privs_are('public', 'cancel_friend_request', array['uuid'], 'authenticated', array['EXECUTE'], 'authenticated darf cancel_friend_request');
select function_privs_are('public', 'consume_rate_limit', array['uuid', 'text', 'integer', 'integer'], 'authenticated', '{}', 'authenticated darf das Aufruflimit NICHT direkt ändern');
select function_privs_are('public', 'consume_rate_limit', array['uuid', 'text', 'integer', 'integer'], 'service_role', array['EXECUTE'], 'service_role darf das Aufruflimit nutzen (notify-friends)');

select is(
  (select coalesce(array_agg(format('%s.%s', n.nspname, p.proname) order by n.nspname, p.proname), '{}')
     from pg_catalog.pg_proc p
     join pg_catalog.pg_namespace n on n.oid = p.pronamespace
    where n.nspname in ('public', 'private')
      and (pg_catalog.has_function_privilege('anon', p.oid, 'EXECUTE')
           or pg_catalog.has_function_privilege('public', p.oid, 'EXECUTE'))),
  '{}'::text[],
  'anon/PUBLIC dürfen keine Funktion in public oder private ausführen'
);
select is(
  (select coalesce(array_agg(p.proname::text order by p.proname), '{}')
     from pg_catalog.pg_proc p
    where p.pronamespace = 'public'::regnamespace
      and pg_catalog.has_function_privilege('authenticated', p.oid, 'EXECUTE')),
  array['accept_invite', 'cancel_friend_request', 'create_invite_link', 'list_outgoing_friend_requests',
        'remove_friend', 'respond_friend_request', 'send_friend_request'],
  'authenticated darf in public genau die sieben RPCs ausführen'
);

-- ---------------------------------------------------------------------------------------------------
-- Schema private und Erweiterungen
-- ---------------------------------------------------------------------------------------------------
select schema_privs_are('private', 'authenticated', '{}', 'authenticated hat keinen Zugriff auf das Schema private');
select schema_privs_are('private', 'anon', '{}', 'anon hat keinen Zugriff auf das Schema private');
select is(
  (select e.extnamespace::regnamespace::text from pg_catalog.pg_extension e where e.extname = 'pgcrypto'),
  'extensions',
  'pgcrypto liegt im Schema extensions'
);
select has_extension('pg_cron', 'pg_cron ist installiert');
select has_trigger('auth', 'users', 'on_auth_user_created', 'Trigger legt Profile zu neuen Auth-Nutzern an');
select has_trigger('public', 'drink_events', 'drink_events_limit', 'Trigger begrenzt geteilte Getränke pro Person');
select tables_are('private', array['friend_request_key', 'friend_removals'],
  'Das Schema private enthält genau den Schlüssel für Anfragen und die entfernten Freundschaften');
select is(
  (select coalesce(array_agg(c.relname::text order by c.relname), '{}')
     from pg_catalog.pg_class c
    where c.relnamespace = 'private'::regnamespace
      and c.relkind in ('r', 'p')
      and (pg_catalog.has_table_privilege('authenticated', c.oid, 'SELECT')
           or pg_catalog.has_table_privilege('anon', c.oid, 'SELECT')
           or pg_catalog.has_table_privilege('service_role', c.oid, 'SELECT'))),
  '{}'::text[],
  'Tabellen in private: keine Rechte für anon, authenticated oder service_role'
);
select is((select count(*)::int from private.friend_request_key), 1, 'Genau ein Schlüssel für die Anfrage-Kennungen');
select is((select octet_length(key) from private.friend_request_key), 32, 'Der Schlüssel hat 256 bit');

select * from finish();
rollback;
