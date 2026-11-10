import type { ComponentChildren, JSX } from "preact";
import { useEffect, useRef } from "preact/hooks";
import type { DrinkIcon } from "../../core/bar/drinkIcon";
import { useRouter, useServices } from "../context";
import { initials } from "../format";

/** Selbst gezeichnetes Getränke-Icon aus design/icons (beim Build nach icons/<key>.svg kopiert). */
export function DrinkIconImage({ icon, size = 56, label = "" }: { icon: DrinkIcon; size?: number; label?: string }) {
  const { config } = useServices();
  return (
    <img
      class="drink-icon"
      src={`${config.basePath}icons/${icon}.svg`}
      width={size}
      height={size}
      alt={label}
      decoding="async"
      draggable={false}
    />
  );
}

/** Interner Link über den Router (normale Links für Screenreader und Mittelklick). */
export function Link({ to, children, class: className, ...rest }: { to: string; children: ComponentChildren; class?: string } & Omit<JSX.HTMLAttributes<HTMLAnchorElement>, "href" | "class">) {
  const router = useRouter();
  const href = router.href(to);
  return (
    <a
      {...rest}
      class={className}
      href={href}
      onClick={(event) => {
        if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
        event.preventDefault();
        router.navigate(to);
      }}
    >
      {children}
    </a>
  );
}

/** Seitenkopf: h1 bekommt nach jedem Seitenwechsel den Fokus (für Screenreader und Tastatur). */
export function PageHeader({ title, children }: { title: string; children?: ComponentChildren }) {
  const heading = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    if (document.body.dataset.navigated === "true") heading.current?.focus({ preventScroll: false });
  }, [title]);
  return (
    <header class="page-header">
      <h1 ref={heading} tabIndex={-1}>
        {title}
      </h1>
      {children}
    </header>
  );
}

export function Section({ title, children, id, headingLevel = 2 }: { title: string; children: ComponentChildren; id?: string; headingLevel?: 2 | 3 }) {
  const Heading = headingLevel === 2 ? "h2" : "h3";
  const headingId = id ? `${id}-titel` : undefined;
  return (
    <section class="card" id={id} aria-labelledby={headingId}>
      <Heading id={headingId}>{title}</Heading>
      {children}
    </section>
  );
}

export function Notice({ children, tone = "info", role }: { children: ComponentChildren; tone?: "info" | "warning" | "error" | "success"; role?: "status" | "alert" }) {
  return (
    <div class={`notice notice-${tone}`} role={role}>
      {children}
    </div>
  );
}

/** Fehlermeldung (für Screenreader sofort angesagt). */
export function ErrorText({ error }: { error: string | null }) {
  if (!error) return null;
  return (
    <p class="error-text" role="alert">
      {error}
    </p>
  );
}

/** Platzhalter mit Anfangsbuchstaben statt eines Profilbilds – lädt keine Bilder von fremden Servern. */
export function Avatar({ name }: { name: string }) {
  return (
    <span class="avatar" aria-hidden="true">
      {initials(name)}
    </span>
  );
}

export function Spinner({ label }: { label: string }) {
  return (
    <p class="loading" role="status">
      <span class="spinner" aria-hidden="true" />
      {label}
    </p>
  );
}

export function ServerNotConfigured() {
  return (
    <Notice tone="info" role="status">
      <p>
        <strong>Server noch nicht eingerichtet.</strong>
      </p>
      <p>
        Freunde und Benachrichtigungen sind in dieser Version noch nicht verfügbar. Bar, Verlauf, Statistik und Vorlesen
        funktionieren trotzdem vollständig – alles bleibt auf diesem Gerät.
      </p>
    </Notice>
  );
}

/** Hinweis für das iPhone: Benachrichtigungen erst nach „Zum Home-Bildschirm“ (iOS 16.4+). */
export function IosHomeScreenHint() {
  return (
    <Notice tone="info">
      <p>
        <strong>Zum Home-Bildschirm hinzufügen</strong>
      </p>
      <p>
        Auf dem iPhone gibt es Benachrichtigungen nur, wenn Drink With Me auf dem Home-Bildschirm liegt: Tippe in Safari auf „Teilen“ und
        dann auf „Zum Home-Bildschirm“. Öffne die App danach über das neue Symbol.
      </p>
    </Notice>
  );
}

/** Kleine, selbst gezeichnete Linien-Icons für Navigation und Knöpfe (CC0). */
export function UiIcon({ name }: { name: "bar" | "history" | "friends" | "settings" | "mic" | "speaker" | "share" | "plus" | "up" | "down" | "edit" | "trash" | "undo" | "back" }) {
  const common = { width: 24, height: 24, viewBox: "0 0 24 24", fill: "none", stroke: "currentColor", "stroke-width": 2, "stroke-linecap": "round" as const, "stroke-linejoin": "round" as const, "aria-hidden": "true" as const, focusable: "false" as const };
  switch (name) {
    case "bar":
      return (
        <svg {...common}>
          <path d="M6 3h12l-1.6 16.2a2 2 0 0 1-2 1.8H9.6a2 2 0 0 1-2-1.8z" />
          <path d="M6.6 9h10.8" />
        </svg>
      );
    case "history":
      return (
        <svg {...common}>
          <path d="M4 20h16" />
          <path d="M7 20v-6" />
          <path d="M12 20V8" />
          <path d="M17 20v-9" />
        </svg>
      );
    case "friends":
      return (
        <svg {...common}>
          <circle cx="9" cy="8" r="3.2" />
          <path d="M3 20c.6-3.6 3-5.6 6-5.6s5.4 2 6 5.6" />
          <circle cx="17" cy="9" r="2.6" />
          <path d="M16.4 14.5c2.4.2 4 1.9 4.6 4.5" />
        </svg>
      );
    case "settings":
      return (
        <svg {...common}>
          <path d="M4 7h10" />
          <path d="M18 7h2" />
          <circle cx="16" cy="7" r="2" />
          <path d="M4 17h3" />
          <path d="M11 17h9" />
          <circle cx="9" cy="17" r="2" />
        </svg>
      );
    case "mic":
      return (
        <svg {...common}>
          <rect x="9" y="3" width="6" height="11" rx="3" />
          <path d="M5.5 11a6.5 6.5 0 0 0 13 0" />
          <path d="M12 17.5V21" />
        </svg>
      );
    case "speaker":
      return (
        <svg {...common}>
          <path d="M4 9.5h3.5L12 5v14l-4.5-4.5H4z" />
          <path d="M15.5 9a4 4 0 0 1 0 6" />
          <path d="M18 6.5a7.5 7.5 0 0 1 0 11" />
        </svg>
      );
    case "share":
      return (
        <svg {...common}>
          <path d="M12 15V3" />
          <path d="M7.5 7.5 12 3l4.5 4.5" />
          <path d="M5 12v7a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-7" />
        </svg>
      );
    case "plus":
      return (
        <svg {...common}>
          <path d="M12 5v14" />
          <path d="M5 12h14" />
        </svg>
      );
    case "up":
      return (
        <svg {...common}>
          <path d="m6 14 6-6 6 6" />
        </svg>
      );
    case "down":
      return (
        <svg {...common}>
          <path d="m6 10 6 6 6-6" />
        </svg>
      );
    case "edit":
      return (
        <svg {...common}>
          <path d="M4 20h4L19 9a2.8 2.8 0 0 0-4-4L4 16z" />
        </svg>
      );
    case "trash":
      return (
        <svg {...common}>
          <path d="M4 7h16" />
          <path d="M9 7V4h6v3" />
          <path d="M6 7l1 13h10l1-13" />
        </svg>
      );
    case "undo":
      return (
        <svg {...common}>
          <path d="M9 14 4 9l5-5" />
          <path d="M4 9h10a6 6 0 0 1 0 12h-3" />
        </svg>
      );
    case "back":
      return (
        <svg {...common}>
          <path d="m15 18-6-6 6-6" />
        </svg>
      );
  }
}
