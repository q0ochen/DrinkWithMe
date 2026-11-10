-- Drink With Me – Tabellen (Schema public), Constraints und Indizes.
-- Vertrag: docs/ARCHITEKTUR.md, Abschnitt 2.1.
--
-- Sicherheitsgrundsatz: Jede Tabelle bekommt sofort Row Level Security und zunächst KEINE Rechte für
-- Clients (anon/authenticated). Die erlaubten Zugriffe werden in 20261110120200_zugriffsrechte.sql
-- einzeln freigegeben. Die Trinkhistorie liegt nur auf dem Gerät; hier liegen nur kurzlebige,
-- geteilte Ereignisse (30 Tage).

-- ---------------------------------------------------------------------------------------------------
-- Profile: werden per Trigger auf auth.users angelegt. KEIN E-Mail-Feld (E-Mails bleiben in auth.users).
-- ---------------------------------------------------------------------------------------------------
create table public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  display_name text not null
    constraint profiles_display_name_laenge check (char_length(display_name) between 1 and 50)
    constraint profiles_display_name_nicht_leer check (btrim(display_name) <> '')
    constraint profiles_display_name_ohne_steuerzeichen check (display_name !~ '[[:cntrl:]]'),
  avatar_url text null
    constraint profiles_avatar_url_laenge check (char_length(avatar_url) <= 2048)
    constraint profiles_avatar_url_https check (avatar_url ~* '^https://'),
  created_at timestamptz not null default now()
);
comment on table public.profiles is
  'Öffentliches Profil (Anzeigename, Bild). Sichtbar für sich selbst, Freunde und bei offener Anfrage.';

-- ---------------------------------------------------------------------------------------------------
-- Freundschaften: ein Datensatz pro Paar, immer mit user_a < user_b. Schreiben nur über RPCs.
-- ---------------------------------------------------------------------------------------------------
create table public.friendships (
  user_a uuid not null references public.profiles (id) on delete cascade,
  user_b uuid not null references public.profiles (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_a, user_b),
  constraint friendships_geordnet check (user_a < user_b)
);
-- Der Primärschlüssel deckt Abfragen über user_a ab, dieser Index die über user_b.
create index friendships_user_b_idx on public.friendships (user_b);
comment on table public.friendships is
  'Bestätigte Freundschaften (user_a < user_b). Anlegen/Löschen nur über RPCs.';

-- ---------------------------------------------------------------------------------------------------
-- Freundschaftsanfragen per E-Mail. Anlegen/Annehmen nur über RPCs, Zurückziehen per DELETE.
-- ---------------------------------------------------------------------------------------------------
create table public.friend_requests (
  id uuid primary key default gen_random_uuid(),
  from_user uuid not null references public.profiles (id) on delete cascade,
  to_user uuid not null references public.profiles (id) on delete cascade,
  created_at timestamptz not null default now(),
  constraint friend_requests_einmalig unique (from_user, to_user),
  constraint friend_requests_nicht_an_sich_selbst check (from_user <> to_user)
);
create index friend_requests_to_user_idx on public.friend_requests (to_user);
comment on table public.friend_requests is
  'Offene Freundschaftsanfragen. Lesen: Absender und Empfänger. Löschen: Absender.';

-- ---------------------------------------------------------------------------------------------------
-- Einladungs-Links: Token ≥ 128 bit (base64url), 14 Tage gültig, höchstens einer pro Nutzer.
-- Kein Client-Zugriff außer über RPCs.
-- ---------------------------------------------------------------------------------------------------
create table public.invite_links (
  token text primary key
    -- 22 base64url-Zeichen entsprechen 132 bit; erzeugt werden 32 Zeichen (192 bit).
    constraint invite_links_token_format check (token ~ '^[A-Za-z0-9_-]{22,128}$'),
  owner uuid not null references public.profiles (id) on delete cascade,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null default (now() + interval '14 days'),
  constraint invite_links_ablauf_nach_erstellung check (expires_at > created_at)
);
-- Pro Nutzer höchstens ein Link: ein neuer Link ersetzt den alten.
create unique index invite_links_owner_key on public.invite_links (owner);
create index invite_links_expires_at_idx on public.invite_links (expires_at);
comment on table public.invite_links is
  'Einladungs-Links (ein Link pro Nutzer, 14 Tage gültig). Nur über RPCs erreichbar.';

-- ---------------------------------------------------------------------------------------------------
-- Geteilte Trink-Ereignisse (kurzlebig, 30 Tage). id = lokale DrinkEntry.id.
-- Geteilt werden nur Name, Icon, Kategorie, Menge in ml und Zeitpunkt – nie Gramm/Zucker/Statistiken.
-- ---------------------------------------------------------------------------------------------------
create table public.drink_events (
  id uuid primary key,
  author_id uuid not null default auth.uid() references public.profiles (id) on delete cascade,
  name text not null
    constraint drink_events_name_laenge check (char_length(name) between 1 and 40)
    constraint drink_events_name_nicht_leer check (btrim(name) <> '')
    constraint drink_events_name_ohne_steuerzeichen check (name !~ '[[:cntrl:]]'),
  icon text not null
    constraint drink_events_icon_format check (icon ~ '^[a-z_]{1,30}$'),
  category text not null
    constraint drink_events_category_gueltig check (category in (
      'WATER', 'COFFEE', 'TEA', 'JUICE', 'SOFT_DRINK', 'ENERGY_DRINK', 'MILK',
      'BEER', 'WINE', 'SPARKLING_WINE', 'SPIRIT', 'COCKTAIL', 'OTHER'
    )),
  volume_ml integer not null
    constraint drink_events_volume_ml_bereich check (volume_ml between 1 and 2000),
  consumed_at timestamptz not null,
  created_at timestamptz not null default now(),
  notified_at timestamptz null
);
-- Feed der Freunde (neueste zuerst) und tägliches Aufräumen.
create index drink_events_author_consumed_idx on public.drink_events (author_id, consumed_at desc);
create index drink_events_consumed_at_idx on public.drink_events (consumed_at);
comment on table public.drink_events is
  'Mit Freunden geteilte Getränke (nur Name, Icon, Kategorie, ml, Zeitpunkt). Löschung nach 30 Tagen.';

-- ---------------------------------------------------------------------------------------------------
-- Push-Abos (UnifiedPush oder Web Push, beide mit VAPID/RFC 8291). Höchstens 5 pro Nutzer (Trigger).
-- ---------------------------------------------------------------------------------------------------
create table public.push_subscriptions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references public.profiles (id) on delete cascade,
  endpoint text not null
    constraint push_subscriptions_endpoint_einmalig unique
    constraint push_subscriptions_endpoint_https check (endpoint ~* '^https://' and char_length(endpoint) <= 2048),
  -- Öffentlicher P-256-Schlüssel des Empfängers (65 Byte, base64url bzw. base64 → 86–88 Zeichen).
  p256dh text not null
    constraint push_subscriptions_p256dh_format check (p256dh ~ '^[A-Za-z0-9_+/-]{86,88}={0,2}$'),
  -- Authentifizierungsgeheimnis des Empfängers (16 Byte → 22–24 Zeichen).
  auth text not null
    constraint push_subscriptions_auth_format check (auth ~ '^[A-Za-z0-9_+/-]{21,24}={0,2}$'),
  platform text not null
    constraint push_subscriptions_platform_gueltig check (platform in ('unifiedpush', 'web')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index push_subscriptions_user_id_idx on public.push_subscriptions (user_id);
comment on table public.push_subscriptions is
  'Push-Abos (höchstens 5 pro Nutzer). Nur der Besitzer liest/legt an/löscht; Versand per Service-Rolle.';

-- ---------------------------------------------------------------------------------------------------
-- Zähler für Aufruflimits (festes Zeitfenster pro Nutzer und Aktion). Kein Client-Zugriff.
-- ---------------------------------------------------------------------------------------------------
create table public.rate_limits (
  user_id uuid not null references public.profiles (id) on delete cascade,
  action text not null
    constraint rate_limits_action_format check (action ~ '^[a-z_]{1,40}$'),
  window_start timestamptz not null,
  count integer not null
    constraint rate_limits_count_positiv check (count >= 0),
  primary key (user_id, action)
);
comment on table public.rate_limits is
  'Aufruflimits pro Nutzer und Aktion (festes Zeitfenster). Nur für Server-Funktionen.';

-- ---------------------------------------------------------------------------------------------------
-- Row Level Security auf JEDER Tabelle; zunächst keinerlei Client-Rechte.
-- ---------------------------------------------------------------------------------------------------
alter table public.profiles enable row level security;
alter table public.friendships enable row level security;
alter table public.friend_requests enable row level security;
alter table public.invite_links enable row level security;
alter table public.drink_events enable row level security;
alter table public.push_subscriptions enable row level security;
alter table public.rate_limits enable row level security;

revoke all on table
  public.profiles, public.friendships, public.friend_requests, public.invite_links,
  public.drink_events, public.push_subscriptions, public.rate_limits
  from public, anon, authenticated, service_role;
