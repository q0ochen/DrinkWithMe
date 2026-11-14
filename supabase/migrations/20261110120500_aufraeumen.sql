-- Drink With Me – Tägliches Aufräumen per pg_cron (Datensparsamkeit).
-- Vertrag: docs/ARCHITEKTUR.md, Abschnitt 2.1 (drink_events nach 30 Tagen löschen).

/** Löscht geteilte Ereignisse, die älter als 30 Tage sind, abgelaufene Einladungs-Links und veraltete
    Zähler der Aufruflimits (alle Fenster sind höchstens einen Tag lang). */
create or replace function private.cleanup_expired_data()
returns void
language plpgsql
set search_path = ''
as $$
begin
  delete from public.drink_events e where e.consumed_at < now() - interval '30 days';
  delete from public.invite_links i where i.expires_at <= now();
  delete from public.rate_limits r where r.window_start < now() - interval '2 days';
end;
$$;

revoke all on function private.cleanup_expired_data() from public;

-- Täglich um 03:17 UTC. cron.schedule mit Namen ist idempotent (ein vorhandener Job wird aktualisiert).
select cron.schedule(
  'drinkwithme-aufraeumen',
  '17 3 * * *',
  'select private.cleanup_expired_data()'
);
