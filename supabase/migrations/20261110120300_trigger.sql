-- Drink With Me – Trigger: Profil zu jedem neuen Auth-Nutzer, höchstens 5 Push-Abos pro Nutzer,
-- updated_at pflegen. Vertrag: docs/ARCHITEKTUR.md, Abschnitt 2.1.
--
-- Alle Funktionen: search_path = '' und vollständig qualifizierte Namen (Schutz vor Search-Path-Angriffen).

-- ---------------------------------------------------------------------------------------------------
-- Anzeigename und Bild aus raw_user_meta_data (Google liefert full_name/name und avatar_url/picture).
-- ---------------------------------------------------------------------------------------------------

/** Anzeigename aus den Google-Metadaten: full_name, sonst name; Steuerzeichen und doppelte Leerzeichen
    werden entfernt, höchstens 50 Zeichen. Ohne Namen: „Drink-With-Me-Nutzer“ (nie die E-Mail-Adresse). */
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
  return 'Drink-With-Me-Nutzer';
end;
$$;

/** Profilbild aus den Google-Metadaten: avatar_url, sonst picture; nur https und höchstens 2048 Zeichen,
    sonst NULL. */
create or replace function private.avatar_url_from_metadata(meta jsonb)
returns text
language plpgsql
immutable
set search_path = ''
as $$
declare
  kandidat text;
begin
  foreach kandidat in array array[meta ->> 'avatar_url', meta ->> 'picture'] loop
    kandidat := btrim(coalesce(kandidat, ''));
    if kandidat ~* '^https://[^[:space:][:cntrl:]]+$' and char_length(kandidat) <= 2048 then
      return kandidat;
    end if;
  end loop;
  return null;
end;
$$;

/** Legt zu jedem neuen Eintrag in auth.users das Profil an. Läuft als Eigentümer (SECURITY DEFINER),
    weil GoTrue Nutzer als supabase_auth_admin anlegt. */
create or replace function private.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.profiles (id, display_name, avatar_url)
  values (
    new.id,
    private.display_name_from_metadata(coalesce(new.raw_user_meta_data, '{}'::jsonb)),
    private.avatar_url_from_metadata(coalesce(new.raw_user_meta_data, '{}'::jsonb))
  )
  on conflict (id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function private.handle_new_user();

-- Nutzer, die schon vor dieser Migration existierten (z. B. ein Test-Login), bekommen ebenfalls ein Profil.
insert into public.profiles (id, display_name, avatar_url)
select
  u.id,
  private.display_name_from_metadata(coalesce(u.raw_user_meta_data, '{}'::jsonb)),
  private.avatar_url_from_metadata(coalesce(u.raw_user_meta_data, '{}'::jsonb))
from auth.users u
on conflict (id) do nothing;

-- ---------------------------------------------------------------------------------------------------
-- Höchstens 5 Push-Abos pro Nutzer.
-- ---------------------------------------------------------------------------------------------------

/** Verhindert ein sechstes Push-Abo desselben Nutzers (Fehler „too_many_subscriptions“). Eine
    Advisory-Sperre pro Nutzer verhindert, dass parallele Anmeldungen das Limit umgehen. */
create or replace function private.enforce_push_subscription_limit()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtextextended('drinkwithme.push_subscriptions:' || new.user_id::text, 0)
  );
  if (select count(*) from public.push_subscriptions s where s.user_id = new.user_id) >= 5 then
    raise exception 'too_many_subscriptions'
      using errcode = 'P0001',
            detail = 'Höchstens 5 Push-Abos pro Nutzer. Bitte zuerst ein altes Abo löschen.';
  end if;
  return new;
end;
$$;

drop trigger if exists push_subscriptions_limit on public.push_subscriptions;
create trigger push_subscriptions_limit
  before insert on public.push_subscriptions
  for each row execute function private.enforce_push_subscription_limit();

/** Setzt updated_at bei jeder Änderung (nur Server-Funktionen dürfen Push-Abos ändern). */
create or replace function private.touch_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

drop trigger if exists push_subscriptions_touch_updated_at on public.push_subscriptions;
create trigger push_subscriptions_touch_updated_at
  before update on public.push_subscriptions
  for each row execute function private.touch_updated_at();

-- Interne Funktionen: niemand außer dem Eigentümer darf sie direkt ausführen.
revoke all on function private.display_name_from_metadata(jsonb) from public;
revoke all on function private.avatar_url_from_metadata(jsonb) from public;
revoke all on function private.handle_new_user() from public;
revoke all on function private.enforce_push_subscription_limit() from public;
revoke all on function private.touch_updated_at() from public;
