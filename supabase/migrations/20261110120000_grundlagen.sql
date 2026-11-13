-- Drink With Me – Grundlagen: Erweiterungen und internes Schema.
-- Vertrag: docs/ARCHITEKTUR.md, Abschnitt 2.

-- pgcrypto liefert gen_random_bytes() für die Einladungs-Tokens (Supabase: Schema "extensions").
create extension if not exists pgcrypto with schema extensions;

-- pg_cron räumt täglich abgelaufene Daten auf (siehe 20261110120500_aufraeumen.sql).
create extension if not exists pg_cron with schema pg_catalog;

-- Internes Schema für Trigger- und Hilfsfunktionen. Es ist NICHT in [api].schemas eingetragen und wird
-- deshalb von der Data API (PostgREST) nicht ausgeliefert; Clients können diese Funktionen nicht aufrufen.
create schema if not exists private;
revoke all on schema private from public;
revoke all on schema private from anon, authenticated;
comment on schema private is
  'Drink With Me: interne Hilfsfunktionen (Trigger, Aufräumen). Nicht über die Data API erreichbar.';
