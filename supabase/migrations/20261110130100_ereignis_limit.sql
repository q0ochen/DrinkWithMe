-- Drink With Me – Höchstens 200 geteilte Getränke pro Person in 24 Stunden.
-- Vertrag: docs/ARCHITEKTUR.md, Abschnitt 2.1 (Review 3, Befund 7).
--
-- drink_events war die einzige Tabelle, in die Clients ohne Obergrenze schreiben konnten. Ein einziges Konto hätte
-- mit Massen-Inserts die 500-MB-Datenbank des Free-Tarifs füllen können; Supabase schaltet das Projekt dann für
-- alle in den Nur-Lesen-Modus. 200 Getränke am Tag teilt kein Mensch – das Limit stört also niemanden.

/** Verhindert mehr als 200 geteilte Getränke desselben Autors innerhalb von 24 Stunden (Fehler „too_many_events“).
    Gezählt wird nach created_at (Zeitpunkt des Teilens). Eine Advisory-Sperre pro Autor verhindert, dass parallele
    Anfragen das Limit umgehen; Zeilen desselben Massen-Inserts zählen mit. */
create or replace function private.enforce_drink_event_limit()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtextextended('drinkwithme.drink_events:' || new.author_id::text, 0)
  );
  if (
    select count(*)
    from public.drink_events e
    where e.author_id = new.author_id
      and e.created_at > now() - interval '24 hours'
      -- Redundant, aber über den Index (author_id, consumed_at) schnell: Beim Anlegen gilt
      -- consumed_at >= now() - 24 h, also liegt consumed_at jeder hier gezählten Zeile in den letzten 48 h.
      and e.consumed_at > now() - interval '48 hours'
  ) >= 200 then
    raise exception 'too_many_events'
      using errcode = 'P0001',
            detail = 'Höchstens 200 geteilte Getränke in 24 Stunden.';
  end if;
  return new;
end;
$$;

revoke all on function private.enforce_drink_event_limit() from public;

drop trigger if exists drink_events_limit on public.drink_events;
create trigger drink_events_limit
  before insert on public.drink_events
  for each row execute function private.enforce_drink_event_limit();
