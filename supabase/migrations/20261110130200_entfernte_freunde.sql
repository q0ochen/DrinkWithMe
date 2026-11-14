-- Drink With Me – Wer als Freund entfernt wurde, kann sich nicht mit einem alten Einladungs-Link zurückholen.
-- Vertrag: docs/ARCHITEKTUR.md, Abschnitt 2.2 (Review 3, Befund 8).
--
-- Vorher: Nach remove_friend blieb der Einladungs-Link der entfernenden Person bis zu 14 Tage gültig. Die entfernte
-- Person konnte ihn erneut annehmen (accept_invite) und war ohne Zustimmung sofort wieder befreundet.
--
-- Jetzt merkt sich remove_friend, wer wen entfernt hat. accept_invite lehnt einen Link mit „invite_invalid“ ab,
-- wenn sein Besitzer den Annehmenden entfernt hat, NACHDEM der Link erzeugt wurde. Ein danach erzeugter Link ist eine
-- neue Zustimmung und gilt wieder; für alle anderen gilt der alte Link unverändert weiter. Werden beide wieder
-- Freunde, verschwindet der Vermerk. Vermerke älter als 14 Tage löscht das tägliche Aufräumen (dann ist jeder
-- ältere Link ohnehin abgelaufen).

create table private.friend_removals (
  remover uuid not null references public.profiles (id) on delete cascade,
  removed uuid not null references public.profiles (id) on delete cascade,
  removed_at timestamptz not null default now(),
  primary key (remover, removed),
  constraint friend_removals_nicht_sich_selbst check (remover <> removed)
);
-- Der Primärschlüssel deckt die Abfrage in accept_invite ab, dieser Index das Löschen eines Kontos.
create index friend_removals_removed_idx on private.friend_removals (removed);
create index friend_removals_removed_at_idx on private.friend_removals (removed_at);
alter table private.friend_removals enable row level security;
revoke all on table private.friend_removals from public, anon, authenticated, service_role;
comment on table private.friend_removals is
  'Wer wen als Freund entfernt hat (für accept_invite). Nur intern; nach 14 Tagen gelöscht.';

/** Legt die Freundschaft zwischen a und b an (idempotent), löscht offene Anfragen in beide Richtungen und Vermerke
    über ein früheres Entfernen zwischen beiden. */
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

  delete from private.friend_removals e
  where (e.remover = a and e.removed = b)
     or (e.remover = b and e.removed = a);
end;
$$;

-- ---------------------------------------------------------------------------------------------------
-- accept_invite(token text) → profiles
-- ---------------------------------------------------------------------------------------------------

/** Nimmt eine Einladung an: Ist der Link gültig und nicht der eigene, entsteht sofort die Freundschaft
    (der Einladende hat durch das Teilen zugestimmt, der Empfänger durch das Antippen). Gibt das Profil des
    Einladenden zurück. Fehler: „invite_invalid“ (unbekannt/abgelaufen, oder der Einladende hat den Annehmenden
    entfernt, nachdem er den Link erzeugt hatte), „invite_own“ (eigener Link), „rate_limited“ (mehr als 30 Aufrufe
    pro Tag). */
create or replace function public.accept_invite(token text)
returns public.profiles
language plpgsql
security definer
set search_path = ''
as $$
declare
  ich uuid := private.require_user();
  einladender uuid;
  link_erzeugt timestamptz;
  ergebnis public.profiles;
begin
  if not public.consume_rate_limit(ich, 'accept_invite', 30, 86400) then
    raise exception 'rate_limited' using errcode = 'P0001';
  end if;

  select i.owner, i.created_at into einladender, link_erzeugt
  from public.invite_links i
  where i.token = btrim(coalesce(accept_invite.token, ''))
    and i.expires_at > now();

  if einladender is null then
    raise exception 'invite_invalid' using errcode = 'P0001';
  end if;
  if einladender = ich then
    raise exception 'invite_own' using errcode = 'P0001';
  end if;
  -- Entfernt, nachdem der Link erzeugt wurde: Dieser Link gilt für diese Person nicht mehr.
  if exists (
    select 1 from private.friend_removals e
    where e.remover = einladender
      and e.removed = ich
      and e.removed_at >= link_erzeugt
  ) then
    raise exception 'invite_invalid' using errcode = 'P0001';
  end if;

  perform private.make_friends(ich, einladender);

  select p.* into ergebnis from public.profiles p where p.id = einladender;
  return ergebnis;
end;
$$;

-- ---------------------------------------------------------------------------------------------------
-- remove_friend(friend_id uuid) → void
-- ---------------------------------------------------------------------------------------------------

/** Beendet die Freundschaft mit friend_id und vermerkt das Entfernen (siehe accept_invite). Idempotent: Ohne
    Freundschaft passiert nichts, auch kein Vermerk. */
create or replace function public.remove_friend(friend_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  ich uuid := private.require_user();
  entfernt integer;
begin
  delete from public.friendships f
  where f.user_a = least(ich, remove_friend.friend_id)
    and f.user_b = greatest(ich, remove_friend.friend_id);
  get diagnostics entfernt = row_count;

  if entfernt > 0 then
    insert into private.friend_removals (remover, removed, removed_at)
    values (ich, remove_friend.friend_id, now())
    on conflict (remover, removed) do update set removed_at = excluded.removed_at;
  end if;
end;
$$;
