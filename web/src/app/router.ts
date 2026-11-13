import { isValidInviteToken } from "../core/social/inviteLinks";

/**
 * History-Routing unterhalb der Vite-`base` (z. B. /DrinkWithMe/). GitHub Pages liefert für unbekannte Pfade
 * die 404.html (= index.html), daher funktionieren auch /auth/callback und /einladung/<token> direkt.
 */
export type Route =
  | { readonly name: "bar" }
  | { readonly name: "barEdit" }
  | { readonly name: "onboarding" }
  | { readonly name: "history" }
  | { readonly name: "friends" }
  | { readonly name: "settings" }
  | { readonly name: "invite"; readonly token: string }
  | { readonly name: "event"; readonly eventId: string }
  | { readonly name: "authCallback" }
  | { readonly name: "notFound" };

export type MainSection = "bar" | "history" | "friends" | "settings";

const STATIC: Record<string, Route> = {
  "": { name: "bar" },
  bar: { name: "bar" },
  "bar/bearbeiten": { name: "barEdit" },
  willkommen: { name: "onboarding" },
  verlauf: { name: "history" },
  freunde: { name: "friends" },
  einstellungen: { name: "settings" },
  "auth/callback": { name: "authCallback" },
};

/** Pfad (ohne Basis) → Route. */
export function parseRoute(pathname: string, basePath: string): Route {
  const base = basePath.endsWith("/") ? basePath : `${basePath}/`;
  let path = pathname;
  if (path === base.slice(0, -1)) path = base;
  if (!path.startsWith(base)) return { name: "notFound" };
  let relative = path.slice(base.length).replace(/\/+$/, "");
  try {
    relative = decodeURIComponent(relative);
  } catch {
    return { name: "notFound" };
  }
  if (relative === "index.html" || relative === "404.html") relative = "";
  const known = STATIC[relative];
  if (known) return known;
  const invite = /^einladung\/([^/]+)$/.exec(relative);
  if (invite) return isValidInviteToken(invite[1]) ? { name: "invite", token: invite[1] } : { name: "notFound" };
  const event = /^ereignis\/([0-9a-fA-F-]{36})$/.exec(relative);
  if (event) return { name: "event", eventId: event[1].toLowerCase() };
  return { name: "notFound" };
}

/** Route → Pfad (ohne Basis, mit führendem „/“), z. B. „/verlauf“. */
export function pathOf(route: Route): string {
  switch (route.name) {
    case "bar":
      return "/";
    case "barEdit":
      return "/bar/bearbeiten";
    case "onboarding":
      return "/willkommen";
    case "history":
      return "/verlauf";
    case "friends":
      return "/freunde";
    case "settings":
      return "/einstellungen";
    case "invite":
      return `/einladung/${route.token}`;
    case "event":
      return `/ereignis/${route.eventId}`;
    case "authCallback":
      return "/auth/callback";
    case "notFound":
      return "/";
  }
}

/** Zu welchem Hauptbereich (untere Navigation) eine Route gehört. */
export function sectionOf(route: Route): MainSection | null {
  switch (route.name) {
    case "bar":
    case "barEdit":
    case "onboarding":
      return "bar";
    case "history":
      return "history";
    case "friends":
    case "invite":
    case "event":
      return "friends";
    case "settings":
      return "settings";
    default:
      return null;
  }
}

/** Vollständige Adresse innerhalb der App: Basis + Pfad. */
export function hrefOf(basePath: string, path: string): string {
  const base = basePath.endsWith("/") ? basePath.slice(0, -1) : basePath;
  return `${base}${path.startsWith("/") ? path : `/${path}`}`;
}

/** Kleiner Router über die History-API. */
export class Router {
  private readonly listeners = new Set<(route: Route) => void>();

  constructor(
    private readonly basePath: string,
    private readonly win: Window = window,
  ) {
    win.addEventListener("popstate", () => this.emit());
  }

  current(): Route {
    return parseRoute(this.win.location.pathname, this.basePath);
  }

  /** Aktueller Pfad ohne Basis (mit Suche), z. B. für die Rückkehr nach der Anmeldung. */
  currentPath(): string {
    const base = this.basePath.endsWith("/") ? this.basePath.slice(0, -1) : this.basePath;
    const path = this.win.location.pathname.startsWith(base) ? this.win.location.pathname.slice(base.length) : "/";
    return path || "/";
  }

  navigate(path: string, options: { replace?: boolean } = {}): void {
    const href = hrefOf(this.basePath, path);
    if (options.replace) this.win.history.replaceState(null, "", href);
    else this.win.history.pushState(null, "", href);
    this.emit();
  }

  href(path: string): string {
    return hrefOf(this.basePath, path);
  }

  subscribe(listener: (route: Route) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private emit(): void {
    const route = this.current();
    for (const listener of this.listeners) listener(route);
  }
}
