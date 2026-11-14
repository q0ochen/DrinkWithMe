-- DrinkWithMe – Neutraler Anzeigename in der neuen Schreibweise „DrinkWithMe-Nutzer“.
-- Vertrag: docs/ARCHITEKTUR.md, Abschnitt 2.1. Ersetzt nur den Rückfallwert aus 20261110120300_trigger.sql;
-- Rechte der Funktion bleiben bei „create or replace“ erhalten.

/** Anzeigename aus den Google-Metadaten: full_name, sonst name; Steuerzeichen und doppelte Leerzeichen
    werden entfernt, höchstens 50 Zeichen. Ohne Namen: „DrinkWithMe-Nutzer“ (nie die E-Mail-Adresse). */
create or replace function private.display_name_from_metadata(meta jsonb)
returns text
language plpgsql
immutable
set search_path = ''
as $$
declare
  kandidat text;
  bereinigt text;
begin
  foreach kandidat in array array[meta ->> 'full_name', meta ->> 'name'] loop
    bereinigt := btrim(regexp_replace(coalesce(kandidat, ''), '[[:cntrl:][:space:]]+', ' ', 'g'));
    if bereinigt <> '' then
      return btrim(left(bereinigt, 50));
    end if;
  end loop;
  return 'DrinkWithMe-Nutzer';
end;
$$;

-- Profile mit dem bisherigen neutralen Namen bekommen den neuen (selbst gewählte Namen bleiben unberührt).
update public.profiles set display_name = 'DrinkWithMe-Nutzer' where display_name = 'Drink-With-Me-Nutzer';
