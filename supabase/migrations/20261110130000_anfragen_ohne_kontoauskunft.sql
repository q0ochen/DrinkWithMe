-- Drink With Me – Freundschaftsanfragen per E-Mail verraten nicht mehr, ob es zu einer Adresse ein Konto gibt.
-- Vertrag: docs/ARCHITEKTUR.md, Abschnitte 2.1 und 2.2 (Review 3, Befund 6).
--
-- Vorher: send_friend_request antwortete zwar immer gleich, legte aber nur bei echten Konten eine Zeile an. Der
-- Absender konnte diese Zeile (mit to_user) lesen und danach das Profil des Empfängers (Google-Name, Bild) – also
-- für jede Adresse prüfen, ob dahinter ein Konto steht, und wem es gehört.
--
-- Jetzt:
-- 1. Jede Eingabe, die wie eine E-Mail-Adresse aussieht, ergibt GENAU EINE Zeile pro Absender und Adresse – ob es
--    ein Konto gibt oder nicht. to_user ist nur gesetzt, wenn die Anfrage jemanden erreicht; sonst NULL (unbekannte,
--    unbestätigte, anonyme oder eigene Adresse, schon befreundet, abgelehnt, Konto gelöscht).
--    Statt der Adresse stehen in der Zeile nur eine Kennung (HMAC-SHA-256 über Absender und Adresse mit einem
--    geheimen Schlüssel – erkennt Wiederholungen) und ein maskierter Hinweis für den Absender („b***@example.org“).
-- 2. Absender sehen ihre Anfragen nur über list_outgoing_friend_requests(): id, email_hint, created_at – nie
--    to_user. Die Tabelle lesen nur noch Empfänger (eingehende Anfragen). Zurückziehen per cancel_friend_request().
-- 3. Profile sieht nur der Empfänger einer Anfrage (Name des Absenders), nicht umgekehrt.
-- 4. Ablehnen löscht die Zeile nicht, sondern nimmt ihr den Empfänger (to_user = NULL, declined_at gesetzt): Für den
--    Absender bleibt sie unverändert; erneutes Senden an dieselbe Adresse erreicht die Person nicht wieder (erst nach
--    Zurückziehen oder Ablauf entsteht eine neue Anfrage). Löscht der Empfänger sein Konto, wird to_user ebenfalls
--    NULL (ON DELETE SET NULL), statt die Zeile zu löschen.
-- 5. Alle Anfragen verfallen nach 30 Tagen (tägliches Aufräumen, siehe 20261110130300_aufraeumen_erweitert.sql) –
--    für alle gleich, damit auch das Verschwinden nichts verrät.

-- ---------------------------------------------------------------------------------------------------
-- Geheimer Schlüssel für die Kennungen (einmal pro Projekt erzeugt, nur für interne Funktionen lesbar)
-- ---------------------------------------------------------------------------------------------------
create table private.friend_request_key (
  id boolean primary key default true
    constraint friend_request_key_einzeilig check (id),
  key bytea not null default extensions.gen_random_bytes(32)
    constraint friend_request_key_laenge check (octet_length(key) = 32)
);
insert into private.friend_request_key default values;
alter table private.friend_request_key enable row level security;
revoke all on table private.friend_request_key from public, anon, authenticated, service_role;
comment on table private.friend_request_key is
  'Geheimer Schlüssel (256 bit) für die HMAC-Kennungen in friend_requests.email_hash. Nie an Clients.';

/** Kennung einer angefragten Adresse: HMAC-SHA-256(Schlüssel, Absender-ID ":" Adresse). Die Absender-ID macht die
    Kennungen verschiedener Absender unvergleichbar; der geheime Schlüssel verhindert das Durchprobieren von
    Adressen, falls nur die Tabelle bekannt wird. Erwartet die Adresse bereits normalisiert (klein, ohne Leerzeichen). */
create or replace function private.friend_request_hash(absender uuid, adresse text)
returns bytea
language sql
stable
set search_path = ''
as $$
  select extensions.hmac(
    convert_to(absender::text || ':' || adresse, 'UTF8'),
    (select k.key from private.friend_request_key k),
    'sha256'
  )
$$;

/** Maskierter Hinweis für den Absender: erstes Zeichen vor dem @, dann „***@“ und die Domain
    („ben.mueller@gmail.com“ → „b***@gmail.com“). */
create or replace function private.email_hint(adresse text)
returns text
language sql
immutable
set search_path = ''
as $$
  select left(split_part(adresse, '@', 1), 1) || '***@' || split_part(adresse, '@', 2)
$$;

revoke all on function private.friend_request_hash(uuid, text) from public;
revoke all on function private.email_hint(text) from public;

-- ---------------------------------------------------------------------------------------------------
-- friend_requests: to_user optional, Kennung und Hinweis, Ablehnungszeitpunkt
-- ---------------------------------------------------------------------------------------------------
alter table public.friend_requests
  alter column to_user drop not null,
  add column email_hash bytea,
  add column email_hint text,
  add column declined_at timestamptz;

-- Bestehende (offene) Anfragen bekommen Kennung und Hinweis aus der Adresse des Empfängers.
update public.friend_requests r
   set email_hash = private.friend_request_hash(r.from_user, lower(btrim(u.email))),
       email_hint = private.email_hint(lower(btrim(u.email)))
  from auth.users u
 where u.id = r.to_user
   and u.email is not null;
delete from public.friend_requests r where r.email_hash is null or r.email_hint is null;

alter table public.friend_requests
  alter column email_hash set not null,
  alter column email_hint set not null,
  add constraint friend_requests_email_hash_laenge check (octet_length(email_hash) = 32),
  add constraint friend_requests_email_hint_format check (
    char_length(email_hint) between 6 and 330 and email_hint !~ '[[:cntrl:]]'
  ),
  add constraint friend_requests_abgelehnt_ohne_empfaenger check (declined_at is null or to_user is null),
  add constraint friend_requests_adresse_einmalig unique (from_user, email_hash);

-- Löscht der Empfänger sein Konto, bleibt die Anfrage beim Absender – nur ohne Empfänger.
alter table public.friend_requests
  drop constraint friend_requests_to_user_fkey,
  add constraint friend_requests_to_user_fkey
    foreign key (to_user) references public.profiles (id) on delete set null;

-- Für das tägliche Löschen nach 30 Tagen.
create index friend_requests_created_at_idx on public.friend_requests (created_at);

comment on table public.friend_requests is
  'Freundschaftsanfragen per E-Mail. Eine Zeile pro Absender und Adresse (auch ohne Konto: to_user NULL). '
  'Lesen: nur der Empfänger. Absender: list_outgoing_friend_requests()/cancel_friend_request(). 30 Tage gültig.';
comment on column public.friend_requests.to_user is
  'Empfänger oder NULL (unbekannte/eigene Adresse, schon befreundet, abgelehnt, Konto gelöscht).';
comment on column public.friend_requests.email_hash is
  'HMAC-SHA-256 über Absender-ID und Adresse (geheimer Schlüssel in private.friend_request_key).';
comment on column public.friend_requests.email_hint is
  'Maskierte Adresse für die Liste des Absenders, z. B. b***@example.org.';
comment on column public.friend_requests.declined_at is
  'Zeitpunkt der Ablehnung (dann ist to_user NULL; die Person wird nicht erneut angefragt).';

-- ---------------------------------------------------------------------------------------------------
-- Rechte: Tabelle nur für Empfänger lesbar und nur die nötigen Spalten; kein DELETE mehr (cancel per RPC)
-- ---------------------------------------------------------------------------------------------------
revoke all on table public.friend_requests from authenticated;
grant select (id, from_user, to_user, created_at) on table public.friend_requests to authenticated;

drop policy "Anfragen lesen: Absender oder Empfänger" on public.friend_requests;
drop policy "Anfragen zurückziehen: nur Absender" on public.friend_requests;
create policy "Anfragen lesen: nur Empfänger"
  on public.friend_requests for select to authenticated
  using (to_user = (select auth.uid()));

drop policy "Profile lesen: selbst, Freunde, offene Anfragen" on public.profiles;
create policy "Profile lesen: selbst, Freunde, Absender eingehender Anfragen"
  on public.profiles for select to authenticated
  using (
    id = (select auth.uid())
    or exists (
      select 1 from public.friendships f
      where (f.user_a = (select auth.uid()) and f.user_b = profiles.id)
         or (f.user_b = (select auth.uid()) and f.user_a = profiles.id)
    )
    or exists (
      select 1 from public.friend_requests r
      where r.to_user = (select auth.uid()) and r.from_user = profiles.id
    )
  );

comment on table public.profiles is
  'Öffentliches Profil (Anzeigename, Bild). Sichtbar für sich selbst, Freunde und Empfänger einer Anfrage.';

-- ---------------------------------------------------------------------------------------------------
-- send_friend_request(email text) → void
-- ---------------------------------------------------------------------------------------------------

/** Freundschaftsanfrage per (Google-)E-Mail-Adresse. Antwortet IMMER gleich und legt für jede gültig aussehende
    Adresse genau eine Anfrage an (wiederholtes Senden ändert nichts) – egal ob es ein Konto gibt. Erreicht wird
    nur ein bestätigtes, fremdes, noch nicht befreundetes Konto; sonst bleibt to_user leer. Gibt es eine
    Gegenanfrage, entsteht sofort die Freundschaft. Limit: 20 Aufrufe pro Nutzer und Tag („rate_limited“). */
create or replace function public.send_friend_request(email text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  ich uuid := private.require_user();
  gesucht text := lower(btrim(coalesce(send_friend_request.email, '')));
  ziel uuid;
begin
  if not public.consume_rate_limit(ich, 'send_friend_request', 20, 86400) then
    raise exception 'rate_limited' using errcode = 'P0001';
  end if;

  -- Was nicht wie eine E-Mail-Adresse aussieht, wird ignoriert. Das hängt nur von der Eingabe ab und verrät nichts.
  if char_length(gesucht) > 320 or gesucht !~ '^[^@[:space:][:cntrl:]]+@[^@[:space:][:cntrl:]]+$' then
    return;
  end if;

  -- Nur bestätigte, nicht gelöschte, nicht anonyme Konten mit Profil.
  select u.id into ziel
  from auth.users u
  join public.profiles p on p.id = u.id
  where lower(u.email) = gesucht
    and u.email_confirmed_at is not null
    and u.deleted_at is null
    and not coalesce(u.is_anonymous, false)
  limit 1;

  -- Gegenanfrage vorhanden: Die andere Person hat schon zugestimmt – sofort befreunden.
  if ziel is not null and ziel <> ich and exists (
    select 1 from public.friend_requests r
    where r.from_user = ziel and r.to_user = ich
  ) then
    perform private.make_friends(ich, ziel);
    return;
  end if;

  -- Niemand zu erreichen: eigene Adresse, schon befreundet oder schon eine offene Anfrage an dieselbe Person
  -- (unter einer anderen Adresse). Die Zeile entsteht trotzdem – nur ohne Empfänger.
  if ziel = ich
     or exists (
       select 1 from public.friendships f
       where f.user_a = least(ich, ziel) and f.user_b = greatest(ich, ziel)
     )
     or exists (
       select 1 from public.friend_requests r
       where r.from_user = ich and r.to_user = ziel
     ) then
    ziel := null;
  end if;

  -- Eine Zeile pro Absender und Adresse. Gab es sie schon ohne Empfänger (Konto damals noch nicht vorhanden) und
  -- wurde sie nicht abgelehnt, erreicht sie jetzt das Konto. Für den Absender ändert sich dabei nichts.
  begin
    insert into public.friend_requests as r (from_user, to_user, email_hash, email_hint)
    values (ich, ziel, private.friend_request_hash(ich, gesucht), private.email_hint(gesucht))
    on conflict (from_user, email_hash) do update
      set to_user = excluded.to_user
      where r.to_user is null and r.declined_at is null and excluded.to_user is not null;
  exception
    when unique_violation then
      null; -- gleichzeitige Anfrage an dieselbe Person: die andere hat gewonnen, Antwort bleibt neutral
  end;
end;
$$;

-- ---------------------------------------------------------------------------------------------------
-- respond_friend_request(request_id uuid, accept boolean) → void
-- ---------------------------------------------------------------------------------------------------

/** Annehmen oder Ablehnen einer Anfrage – nur durch den Empfänger. Annehmen: Freundschaft anlegen und Anfrage
    löschen. Ablehnen: Die Anfrage verliert ihren Empfänger (to_user = NULL, declined_at = jetzt); für den Absender
    sieht sie danach aus wie jede andere, bis sie nach 30 Tagen verfällt. Unbekannte oder fremde Anfrage:
    „request_not_found“. */
create or replace function public.respond_friend_request(request_id uuid, accept boolean)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  ich uuid := private.require_user();
  absender uuid;
begin
  select r.from_user into absender
  from public.friend_requests r
  where r.id = respond_friend_request.request_id
    and r.to_user = ich
  for update;

  if absender is null then
    raise exception 'request_not_found' using errcode = 'P0001';
  end if;

  if coalesce(respond_friend_request.accept, false) then
    perform private.make_friends(ich, absender);
  else
    update public.friend_requests r
       set to_user = null, declined_at = now()
     where r.id = respond_friend_request.request_id;
  end if;
end;
$$;

-- ---------------------------------------------------------------------------------------------------
-- list_outgoing_friend_requests() → setof (id, email_hint, created_at)
-- ---------------------------------------------------------------------------------------------------

/** Eigene ausgehende Anfragen, neueste zuerst: nur ID, maskierte Adresse und Zeitpunkt – nie, ob bzw. wen die
    Anfrage erreicht. */
create or replace function public.list_outgoing_friend_requests()
returns table (id uuid, email_hint text, created_at timestamptz)
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  ich uuid := private.require_user();
begin
  return query
    select r.id, r.email_hint, r.created_at
    from public.friend_requests r
    where r.from_user = ich
    order by r.created_at desc, r.id;
end;
$$;

-- ---------------------------------------------------------------------------------------------------
-- cancel_friend_request(request_id uuid) → void
-- ---------------------------------------------------------------------------------------------------

/** Zieht eine eigene ausgehende Anfrage zurück (löscht sie). Idempotent: unbekannte, fremde oder schon erledigte
    Anfragen werden still ignoriert. */
create or replace function public.cancel_friend_request(request_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  ich uuid := private.require_user();
begin
  delete from public.friend_requests r
  where r.id = cancel_friend_request.request_id
    and r.from_user = ich;
end;
$$;

-- ---------------------------------------------------------------------------------------------------
-- Ausführungsrechte
-- ---------------------------------------------------------------------------------------------------
revoke all on function public.list_outgoing_friend_requests() from public, anon, authenticated, service_role;
revoke all on function public.cancel_friend_request(uuid) from public, anon, authenticated, service_role;
grant execute on function public.list_outgoing_friend_requests() to authenticated;
grant execute on function public.cancel_friend_request(uuid) to authenticated;
