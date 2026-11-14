import { createContext } from "preact";
import { useCallback, useContext, useEffect, useRef, useState } from "preact/hooks";
import type { AppServices } from "../app/container";
import type { Router } from "../app/router";
import type { BarItem } from "../core/bar/barItem";
import { HISTORY_EARLIEST, HISTORY_LATEST, type DrinkEntry } from "../core/log/drinkEntry";
import type { UserProfile } from "../core/social/ports";
import type { AppSettings } from "../data/settingsStore";
import type { SocialServices } from "../social/types";

export const ServicesContext = createContext<AppServices | null>(null);
export const RouterContext = createContext<Router | null>(null);

/** Meldung für Screenreader (aria-live) und die sichtbare Statuszeile. */
export interface Announcer {
  announce(message: string): void;
}
export const AnnouncerContext = createContext<Announcer>({ announce: () => {} });

export function useServices(): AppServices {
  const services = useContext(ServicesContext);
  if (!services) throw new Error("ServicesContext fehlt");
  return services;
}

export function useRouter(): Router {
  const router = useContext(RouterContext);
  if (!router) throw new Error("RouterContext fehlt");
  return router;
}

export function useAnnouncer(): Announcer {
  return useContext(AnnouncerContext);
}

export function useSettings(): AppSettings {
  const { settings } = useServices();
  const [value, setValue] = useState(settings.current());
  useEffect(() => settings.subscribe(setValue), [settings]);
  return value;
}

export function useSessionUser(): UserProfile | null {
  const { session } = useServices();
  const [user, setUser] = useState(session.current());
  useEffect(() => {
    setUser(session.current());
    return session.subscribe(setUser);
  }, [session]);
  return user;
}

/** Lädt die Vernetzung (falls eingerichtet); `undefined` solange sie lädt. */
export function useSocial(): SocialServices | null | undefined {
  const services = useServices();
  const [social, setSocial] = useState<SocialServices | null | undefined>(services.config.supabase ? undefined : null);
  useEffect(() => {
    let active = true;
    services.social().then((value) => {
      if (active) setSocial(value);
    });
    return () => {
      active = false;
    };
  }, [services]);
  return social;
}

/** Bar-Getränke, aktualisiert bei Änderungen. */
export function useBarItems(): BarItem[] | null {
  const { bar, changes } = useServices();
  const [items, setItems] = useState<BarItem[] | null>(null);
  useEffect(() => {
    let active = true;
    const load = () =>
      bar.items().then((loaded) => {
        if (active) setItems(loaded);
      });
    void load();
    const unsubscribe = changes.subscribe("bar", () => void load());
    return () => {
      active = false;
      unsubscribe();
    };
  }, [bar, changes]);
  return items;
}

/** Gesamte lokale Historie (die Statistik braucht alles, siehe StatisticsCalculator). */
export function useHistoryEntries(): DrinkEntry[] | null {
  const { history, changes } = useServices();
  const [entries, setEntries] = useState<DrinkEntry[] | null>(null);
  useEffect(() => {
    let active = true;
    const load = () =>
      history.entries(HISTORY_EARLIEST, HISTORY_LATEST).then((loaded) => {
        if (active) setEntries(loaded);
      });
    void load();
    const unsubscribe = changes.subscribe("history", () => void load());
    return () => {
      active = false;
      unsubscribe();
    };
  }, [history, changes]);
  return entries;
}

/** Aktuelle Zeit, minütlich aktualisiert (für „heute“ und relative Zeiten). */
export function useNow(intervalMillis = 60_000): number {
  const { clock } = useServices();
  const [now, setNow] = useState(clock.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(clock.now()), intervalMillis);
    return () => clearInterval(timer);
  }, [clock, intervalMillis]);
  return now;
}

/** Asynchrone Aktion mit Lade- und Fehlerzustand (verhindert Doppelklicks). */
export function useAction<A extends unknown[]>(action: (...args: A) => Promise<void>): {
  run: (...args: A) => Promise<void>;
  busy: boolean;
  error: unknown;
  clearError: () => void;
} {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const actionRef = useRef(action);
  actionRef.current = action;
  const busyRef = useRef(false);
  const run = useCallback(async (...args: A) => {
    if (busyRef.current) return;
    busyRef.current = true;
    setBusy(true);
    setError(null);
    try {
      await actionRef.current(...args);
    } catch (caught) {
      setError(caught);
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  }, []);
  return { run, busy, error, clearError: () => setError(null) };
}

/** Setzt den Seitentitel (für Screenreader und Verlauf des Browsers). */
export function useDocumentTitle(title: string): void {
  useEffect(() => {
    document.title = title === "Drink With Me" ? title : `${title} – Drink With Me`;
  }, [title]);
}
