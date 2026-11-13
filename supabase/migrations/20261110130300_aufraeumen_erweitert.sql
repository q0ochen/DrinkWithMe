-- Drink With Me – Tägliches Aufräumen, erweitert um Freundschaftsanfragen und Vermerke über entfernte Freunde.
-- Vertrag: docs/ARCHITEKTUR.md, Abschnitt 2.1 (Review 3, Befunde 6 und 8). Der Zeitplan (täglich 03:17 UTC,
-- siehe 20261110120500_aufraeumen.sql) bleibt unverändert.

/** Löscht geteilte Ereignisse und Freundschaftsanfragen, die älter als 30 Tage sind, abgelaufene Einladungs-Links,
    veraltete Zähler der Aufruflimits (alle Fenster sind höchstens einen Tag lang) und Vermerke über entfernte
    Freunde, die älter als 14 Tage sind (jeder davor erzeugte Link ist dann abgelaufen).
    Anfragen verfallen für alle gleich – mit oder ohne Empfänger –, damit ihr Verschwinden nichts verrät. */
create or replace function private.cleanup_expired_data()
returns void
language plpgsql
set search_path = ''
as $$
begin
  delete from public.drink_events e where e.consumed_at < now() - interval '30 days';
  delete from public.friend_requests r where r.created_at < now() - interval '30 days';
  delete from public.invite_links i where i.expires_at <= now();
  delete from public.rate_limits r where r.window_start < now() - interval '2 days';
  delete from private.friend_removals e where e.removed_at < now() - interval '14 days';
end;
$$;
