-- Drink With Me – RPCs für Freundschaften und Einladungen sowie das Aufruflimit.
-- Vertrag: docs/ARCHITEKTUR.md, Abschnitt 2.2.
--
-- Alle RPCs: SECURITY DEFINER (umgehen RLS, prüfen daher alles selbst), search_path = '' und vollständig
-- qualifizierte Namen, ausführbar NUR für authenticated. Fehler werden mit SQLSTATE P0001 und einer
-- maschinenlesbaren Meldung ausgelöst (z. B. „rate_limited“); die Apps übersetzen sie in deutsche Texte.

-- ---------------------------------------------------------------------------------------------------
-- Aufruflimit (festes Zeitfenster) – für die RPCs und für die Edge Function notify-friends.
-- ---------------------------------------------------------------------------------------------------

/** Zählt einen Aufruf von p_action für p_user_id und liefert true, solange höchstens p_max_count Aufrufe
    im aktuellen Zeitfenster (p_window_seconds) liegen. Das Fenster beginnt mit dem ersten Aufruf und
    wird nach Ablauf neu gestartet. Nur für den Server (service_role bzw. andere SECURITY-DEFINER-
    Funktionen), nicht für Clients. */
create or replace function public.consume_rate_limit(
  p_user_id uuid,
  p_action text,
  p_max_count integer,
  p_window_seconds integer
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  fenster interval;
  anzahl integer;
begin
  if p_user_id is null or p_action is null or p_max_count is null or p_max_count < 1
     or p_window_seconds is null or p_window_seconds < 1 then
    raise exception 'invalid_rate_limit_arguments' using errcode = '22023';
  end if;
  fenster := make_interval(secs => p_window_seconds);

  insert into public.rate_limits as r (user_id, action, window_start, count)
  values (p_user_id, p_action, now(), 1)
  on conflict (user_id, action) do update
    set window_start = case when r.window_start <= now() - fenster then now() else r.window_start end,
        count = case when r.window_start <= now() - fenster then 1 else r.count + 1 end
  returning r.count into anzahl;

  return anzahl <= p_max_count;
end;
$$;

-- ---------------------------------------------------------------------------------------------------
-- Hilfsfunktionen (intern)
-- ---------------------------------------------------------------------------------------------------

/** Liefert die ID des angemeldeten Nutzers oder bricht mit „not_authenticated“ ab. */
create or replace function private.require_user()
returns uuid
language plpgsql
stable
set search_path = ''
as $$
declare
  ich uuid := auth.uid();
begin
  if ich is null then
    raise exception 'not_authenticated' using errcode = 'P0001';
  end if;
  return ich;
end;
$$;

/** Legt die Freundschaft zwischen a und b an (idempotent) und löscht offene Anfragen in beide Richtungen. */
create or replace function private.make_friends(a uuid, b uuid)
returns void
language plpgsql
set search_path = ''
as $$
begin
  insert into public.friendships (user_a, user_b)
  values (least(a, b), greatest(a, b))
  on conflict (user_a, user_b) do nothing;

  delete from public.friend_requests r
  where (r.from_user = a and r.to_user = b)
     or (r.from_user = b and r.to_user = a);
end;
$$;

revoke all on function private.require_user() from public;
revoke all on function private.make_friends(uuid, uuid) from public;

-- ---------------------------------------------------------------------------------------------------
-- send_friend_request(email text) → void
-- ---------------------------------------------------------------------------------------------------

/** Freundschaftsanfrage per (Google-)E-Mail-Adresse. Antwortet IMMER gleich und neutral – auch wenn die
    Adresse unbekannt, unbestätigt, die eigene oder schon befreundet ist. Gibt es eine Gegenanfrage, entsteht
    sofort die Freundschaft. Limit: 20 Aufrufe pro Nutzer und Tag (Fehler „rate_limited“). */
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

  if gesucht = '' or char_length(gesucht) > 320 then
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

  if ziel is null or ziel = ich then
    return;
  end if;

  if exists (
    select 1 from public.friendships f
    where f.user_a = least(ich, ziel) and f.user_b = greatest(ich, ziel)
  ) then
    return;
  end if;

  -- Gegenanfrage vorhanden: beide wollen es – sofort befreunden.
  if exists (
    select 1 from public.friend_requests r
    where r.from_user = ziel and r.to_user = ich
  ) then
    perform private.make_friends(ich, ziel);
    return;
  end if;

  insert into public.friend_requests (from_user, to_user)
  values (ich, ziel)
  on conflict (from_user, to_user) do nothing;
end;
$$;

-- ---------------------------------------------------------------------------------------------------
-- respond_friend_request(request_id uuid, accept boolean) → void
-- ---------------------------------------------------------------------------------------------------

/** Annehmen oder Ablehnen einer Anfrage – nur durch den Empfänger. Annehmen: Freundschaft anlegen und
    Anfrage löschen. Ablehnen: Anfrage löschen. Unbekannte oder fremde Anfrage: „request_not_found“. */
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
    delete from public.friend_requests r
    where r.id = respond_friend_request.request_id;
  end if;
end;
$$;

-- ---------------------------------------------------------------------------------------------------
-- create_invite_link() → text
-- ---------------------------------------------------------------------------------------------------

/** Erzeugt ein neues Einladungs-Token (192 bit, base64url, 32 Zeichen), gültig 14 Tage. Ein vorhandener
    Link des Nutzers verfällt sofort. Gibt nur das Token zurück; die App baut daraus die https-URL. */
create or replace function public.create_invite_link()
returns text
language plpgsql
security definer
set search_path = ''
as $$
declare
  ich uuid := private.require_user();
  neues_token text;
begin
  neues_token := rtrim(translate(encode(extensions.gen_random_bytes(24), 'base64'), E'+/\n', '-_'), '=');

  delete from public.invite_links i where i.owner = ich;
  insert into public.invite_links (token, owner, created_at, expires_at)
  values (neues_token, ich, now(), now() + interval '14 days');

  return neues_token;
end;
$$;

-- ---------------------------------------------------------------------------------------------------
-- accept_invite(token text) → profiles
-- ---------------------------------------------------------------------------------------------------

/** Nimmt eine Einladung an: Ist der Link gültig und nicht der eigene, entsteht sofort die Freundschaft
    (der Einladende hat durch das Teilen zugestimmt, der Empfänger durch das Antippen). Gibt das Profil des
    Einladenden zurück. Fehler: „invite_invalid“ (unbekannt/abgelaufen), „invite_own“ (eigener Link),
    „rate_limited“ (mehr als 30 Aufrufe pro Tag). */
create or replace function public.accept_invite(token text)
returns public.profiles
language plpgsql
security definer
set search_path = ''
as $$
declare
  ich uuid := private.require_user();
  einladender uuid;
  ergebnis public.profiles;
begin
  if not public.consume_rate_limit(ich, 'accept_invite', 30, 86400) then
    raise exception 'rate_limited' using errcode = 'P0001';
  end if;

  select i.owner into einladender
  from public.invite_links i
  where i.token = btrim(coalesce(accept_invite.token, ''))
    and i.expires_at > now();

  if einladender is null then
    raise exception 'invite_invalid' using errcode = 'P0001';
  end if;
  if einladender = ich then
    raise exception 'invite_own' using errcode = 'P0001';
  end if;

  perform private.make_friends(ich, einladender);

  select p.* into ergebnis from public.profiles p where p.id = einladender;
  return ergebnis;
end;
$$;

-- ---------------------------------------------------------------------------------------------------
-- remove_friend(friend_id uuid) → void
-- ---------------------------------------------------------------------------------------------------

/** Beendet die Freundschaft mit friend_id (idempotent: ohne Freundschaft passiert nichts). */
create or replace function public.remove_friend(friend_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  ich uuid := private.require_user();
begin
  delete from public.friendships f
  where f.user_a = least(ich, remove_friend.friend_id)
    and f.user_b = greatest(ich, remove_friend.friend_id);
end;
$$;

-- ---------------------------------------------------------------------------------------------------
-- Ausführungsrechte: RPCs nur für authenticated, das Aufruflimit nur für service_role.
-- ---------------------------------------------------------------------------------------------------
revoke all on function public.send_friend_request(text) from public, anon, authenticated, service_role;
revoke all on function public.respond_friend_request(uuid, boolean) from public, anon, authenticated, service_role;
revoke all on function public.create_invite_link() from public, anon, authenticated, service_role;
revoke all on function public.accept_invite(text) from public, anon, authenticated, service_role;
revoke all on function public.remove_friend(uuid) from public, anon, authenticated, service_role;
revoke all on function public.consume_rate_limit(uuid, text, integer, integer) from public, anon, authenticated, service_role;

grant execute on function public.send_friend_request(text) to authenticated;
grant execute on function public.respond_friend_request(uuid, boolean) to authenticated;
grant execute on function public.create_invite_link() to authenticated;
grant execute on function public.accept_invite(text) to authenticated;
grant execute on function public.remove_friend(uuid) to authenticated;
grant execute on function public.consume_rate_limit(uuid, text, integer, integer) to service_role;
