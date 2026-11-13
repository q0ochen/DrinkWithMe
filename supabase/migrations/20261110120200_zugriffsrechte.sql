-- Drink With Me – Zugriffsrechte (GRANTs) und Row-Level-Security-Richtlinien.
-- Vertrag: docs/ARCHITEKTUR.md, Abschnitt 2.1.
--
-- anon (nicht angemeldet) bekommt auf keine Tabelle Rechte. authenticated bekommt nur genau die
-- Operationen, die der Vertrag erlaubt – teils nur für einzelne Spalten. service_role (nur in Edge
-- Functions, nie im Client) darf alles; sie umgeht RLS ohnehin.
-- auth.uid() steht in Richtlinien als (select auth.uid()), damit Postgres den Wert pro Abfrage nur
-- einmal berechnet.

grant select, insert, update, delete on table
  public.profiles, public.friendships, public.friend_requests, public.invite_links,
  public.drink_events, public.push_subscriptions, public.rate_limits
  to service_role;

-- ---------------------------------------------------------------------------------------------------
-- profiles: lesen = selbst, Freunde, offene Anfrage (beide Richtungen); ändern = nur eigene Zeile und
-- nur display_name/avatar_url. Anlegen per Trigger, Löschen per ON DELETE CASCADE.
-- ---------------------------------------------------------------------------------------------------
grant select on table public.profiles to authenticated;
grant update (display_name, avatar_url) on table public.profiles to authenticated;

create policy "Profile lesen: selbst, Freunde, offene Anfragen"
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
      where (r.from_user = (select auth.uid()) and r.to_user = profiles.id)
         or (r.to_user = (select auth.uid()) and r.from_user = profiles.id)
    )
  );

create policy "Profile ändern: nur das eigene"
  on public.profiles for update to authenticated
  using (id = (select auth.uid()))
  with check (id = (select auth.uid()));

-- ---------------------------------------------------------------------------------------------------
-- friendships: lesen, wenn man selbst beteiligt ist. Schreiben nur über RPCs.
-- ---------------------------------------------------------------------------------------------------
grant select on table public.friendships to authenticated;

create policy "Freundschaften lesen: nur eigene"
  on public.friendships for select to authenticated
  using ((select auth.uid()) in (user_a, user_b));

-- ---------------------------------------------------------------------------------------------------
-- friend_requests: lesen = Absender oder Empfänger; löschen (zurückziehen) = Absender.
-- Anlegen und Annehmen/Ablehnen nur über RPCs.
-- ---------------------------------------------------------------------------------------------------
grant select, delete on table public.friend_requests to authenticated;

create policy "Anfragen lesen: Absender oder Empfänger"
  on public.friend_requests for select to authenticated
  using ((select auth.uid()) in (from_user, to_user));

create policy "Anfragen zurückziehen: nur Absender"
  on public.friend_requests for delete to authenticated
  using (from_user = (select auth.uid()));

-- ---------------------------------------------------------------------------------------------------
-- invite_links und rate_limits: kein Client-Zugriff (keine GRANTs, keine Richtlinien).
-- ---------------------------------------------------------------------------------------------------

-- ---------------------------------------------------------------------------------------------------
-- drink_events: anlegen = nur als man selbst und nur mit Zeitpunkt „jetzt“ (letzte 24 h bis +5 min);
-- lesen = Autor oder Freund des Autors; löschen = Autor. KEIN UPDATE.
-- Spaltenrechte: created_at und notified_at setzt nur der Server.
-- ---------------------------------------------------------------------------------------------------
grant select, delete on table public.drink_events to authenticated;
grant insert (id, author_id, name, icon, category, volume_ml, consumed_at)
  on table public.drink_events to authenticated;

create policy "Ereignisse anlegen: nur eigene, Zeitpunkt jetzt"
  on public.drink_events for insert to authenticated
  with check (
    author_id = (select auth.uid())
    and consumed_at >= now() - interval '24 hours'
    and consumed_at <= now() + interval '5 minutes'
  );

create policy "Ereignisse lesen: Autor und Freunde"
  on public.drink_events for select to authenticated
  using (
    author_id = (select auth.uid())
    or exists (
      select 1 from public.friendships f
      where (f.user_a = (select auth.uid()) and f.user_b = drink_events.author_id)
         or (f.user_b = (select auth.uid()) and f.user_a = drink_events.author_id)
    )
  );

create policy "Ereignisse löschen: nur Autor"
  on public.drink_events for delete to authenticated
  using (author_id = (select auth.uid()));

-- ---------------------------------------------------------------------------------------------------
-- push_subscriptions: nur der Besitzer liest, legt an und löscht (max. 5, siehe Trigger). Kein UPDATE.
-- ---------------------------------------------------------------------------------------------------
grant select, delete on table public.push_subscriptions to authenticated;
grant insert (user_id, endpoint, p256dh, auth, platform)
  on table public.push_subscriptions to authenticated;

create policy "Push-Abos lesen: nur eigene"
  on public.push_subscriptions for select to authenticated
  using (user_id = (select auth.uid()));

create policy "Push-Abos anlegen: nur eigene"
  on public.push_subscriptions for insert to authenticated
  with check (user_id = (select auth.uid()));

create policy "Push-Abos löschen: nur eigene"
  on public.push_subscriptions for delete to authenticated
  using (user_id = (select auth.uid()));
