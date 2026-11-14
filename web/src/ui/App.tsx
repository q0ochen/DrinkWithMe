import { useEffect, useMemo, useState } from "preact/hooks";
import type { AppServices } from "../app/container";
import { sectionOf, type MainSection, type Route, type Router } from "../app/router";
import { Link, UiIcon } from "./components/common";
import { AnnouncerContext, RouterContext, ServicesContext, useSettings } from "./context";
import { AuthCallbackScreen } from "./screens/AuthCallbackScreen";
import { BarEditScreen } from "./screens/BarEditScreen";
import { BarScreen } from "./screens/BarScreen";
import { EventScreen } from "./screens/EventScreen";
import { FriendsScreen } from "./screens/FriendsScreen";
import { HistoryScreen } from "./screens/HistoryScreen";
import { InviteScreen } from "./screens/InviteScreen";
import { NotFoundScreen } from "./screens/NotFoundScreen";
import { OnboardingScreen } from "./screens/OnboardingScreen";
import { SettingsScreen } from "./screens/SettingsScreen";

const SECTIONS: ReadonlyArray<{ section: MainSection; path: string; label: string; icon: "bar" | "history" | "friends" | "settings" }> = [
  { section: "bar", path: "/", label: "Bar", icon: "bar" },
  { section: "history", path: "/verlauf", label: "Verlauf", icon: "history" },
  { section: "friends", path: "/freunde", label: "Freunde", icon: "friends" },
  { section: "settings", path: "/einstellungen", label: "Einstellungen", icon: "settings" },
];

function Screen({ route }: { route: Route }) {
  const settings = useSettings();
  // Erster Start: erst die Bar einrichten (Einladungen, Ereignisse und die Anmeldung funktionieren trotzdem).
  const needsOnboarding = !settings.onboardingDone && (route.name === "bar" || route.name === "onboarding" || route.name === "history");
  if (needsOnboarding) return <OnboardingScreen />;
  switch (route.name) {
    case "bar":
    case "onboarding":
      return <BarScreen />;
    case "barEdit":
      return <BarEditScreen />;
    case "history":
      return <HistoryScreen />;
    case "friends":
      return <FriendsScreen />;
    case "settings":
      return <SettingsScreen />;
    case "invite":
      return <InviteScreen token={route.token} />;
    case "event":
      return <EventScreen eventId={route.eventId} />;
    case "authCallback":
      return <AuthCallbackScreen />;
    case "notFound":
      return <NotFoundScreen />;
  }
}

function Navigation({ active, badge }: { active: MainSection | null; badge: number }) {
  return (
    <nav class="main-nav" aria-label="Hauptbereiche">
      <ul>
        {SECTIONS.map((item) => (
          <li key={item.section}>
            <Link to={item.path} class="nav-link" aria-current={active === item.section ? "page" : undefined}>
              <span class="nav-icon">
                <UiIcon name={item.icon} />
                {item.section === "friends" && badge > 0 ? (
                  <span class="nav-badge" aria-hidden="true">
                    {badge > 9 ? "9+" : badge}
                  </span>
                ) : null}
              </span>
              <span class="nav-label">
                {item.label}
                {item.section === "friends" && badge > 0 ? <span class="visually-hidden">, {badge} neue Ereignisse</span> : null}
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </nav>
  );
}

/** Zählt neue Ereignisse der Freunde seit dem letzten Besuch (für das Abzeichen in der Navigation). */
function useUnseenCount(services: AppServices, route: Route): number {
  const [count, setCount] = useState(0);
  useEffect(() => {
    if (!services.config.supabase || !services.session.current() || route.name === "friends") {
      setCount(0);
      return;
    }
    let active = true;
    services
      .social()
      .then(async (social) => {
        if (!social || !social.auth.currentUser()) return;
        const since = services.settings.current().feedLastSeenAt;
        const events = await social.feed.recentEvents(since, 20);
        if (active) setCount(events.length);
      })
      .catch(() => {});
    return () => {
      active = false;
    };
  }, [services, route.name]);
  return count;
}

/** Einstellungen außerhalb des Kontext-Providers beobachten (App setzt den Provider erst). */
function useSettingsOf(services: AppServices) {
  const [value, setValue] = useState(services.settings.current());
  useEffect(() => services.settings.subscribe(setValue), [services]);
  return value;
}

export function App({ services, router }: { services: AppServices; router: Router }) {
  const [route, setRoute] = useState<Route>(router.current());
  const [message, setMessage] = useState("");
  const announcer = useMemo(
    () => ({
      announce(text: string) {
        // Gleiche Meldung zweimal hintereinander soll erneut angesagt werden.
        setMessage("");
        setTimeout(() => setMessage(text), 30);
      },
    }),
    [],
  );

  useEffect(
    () =>
      router.subscribe((next) => {
        document.body.dataset.navigated = "true";
        setRoute(next);
        window.scrollTo({ top: 0 });
      }),
    [router],
  );

  const unseen = useUnseenCount(services, route);
  const settings = useSettingsOf(services);
  const active = sectionOf(route);
  const chrome = route.name !== "authCallback";
  // Beim ersten Start (Bar einrichten) keine Navigation – erst die Bar, dann alles andere.
  const onboarding = !settings.onboardingDone && (route.name === "bar" || route.name === "onboarding" || route.name === "history");

  return (
    <ServicesContext.Provider value={services}>
      <RouterContext.Provider value={router}>
        <AnnouncerContext.Provider value={announcer}>
          <a class="skip-link" href="#inhalt">
            Zum Inhalt springen
          </a>
          <div class={chrome && !onboarding ? "layout" : "layout layout-plain"}>
            {chrome ? (
              <header class="app-bar">
                <Link to="/" class="brand">
                  <img src={`${services.config.basePath}icons/app_icon.svg`} width={36} height={36} alt="" />
                  <span>Drink With Me</span>
                </Link>
                {onboarding ? null : <Navigation active={active} badge={unseen} />}
              </header>
            ) : null}
            <main id="inhalt" class="content" tabIndex={-1}>
              <Screen route={route} />
            </main>
          </div>
          <div class="visually-hidden" role="status" aria-live="polite" aria-atomic="true">
            {message}
          </div>
        </AnnouncerContext.Provider>
      </RouterContext.Provider>
    </ServicesContext.Provider>
  );
}
