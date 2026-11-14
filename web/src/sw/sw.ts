/**
 * Service Worker der Web-App (bewusst klein, ohne Bibliothek):
 * - Offline-Start: alle ausgelieferten Dateien werden beim Installieren zwischengespeichert; Seitenaufrufe gehen
 *   zuerst ans Netz (damit Updates sofort ankommen) und fallen offline auf die gespeicherte index.html zurück.
 * - Web Push: zeigt eine Nachricht nur an, wenn `recipientId` dem angemeldeten Nutzer entspricht; abbestellt wird
 *   nur nach ausdrücklicher Abmeldung.
 * - Tipp auf die Nachricht öffnet den Ereignis-Bildschirm /ereignis/<eventId>.
 */
import { decidePush, parsePushPayload } from "./pushMessages";
import { readRecipient, type RecipientState } from "./recipientStore";

declare const __DWM_PRECACHE__: string[];
declare const __DWM_SW_VERSION__: string;

// Minimale Typen der Service-Worker-Schnittstellen (die DOM-Typen der App enthalten sie nicht).
interface ExtendableEvent extends Event {
  waitUntil(promise: Promise<unknown>): void;
}
interface FetchEvent extends ExtendableEvent {
  readonly request: Request;
  respondWith(response: Response | Promise<Response>): void;
}
interface PushEvent extends ExtendableEvent {
  readonly data: { text(): string } | null;
}
interface NotificationEvent extends ExtendableEvent {
  readonly notification: Notification;
}
interface WindowClient {
  readonly url: string;
  focus(): Promise<WindowClient>;
  navigate(url: string): Promise<WindowClient | null>;
}
interface ServiceWorkerScope {
  readonly registration: ServiceWorkerRegistration;
  readonly clients: {
    matchAll(options: { type: "window"; includeUncontrolled: boolean }): Promise<WindowClient[]>;
    openWindow(url: string): Promise<WindowClient | null>;
    claim(): Promise<void>;
  };
  addEventListener(type: "install" | "activate", listener: (event: ExtendableEvent) => void): void;
  addEventListener(type: "fetch", listener: (event: FetchEvent) => void): void;
  addEventListener(type: "push", listener: (event: PushEvent) => void): void;
  addEventListener(type: "notificationclick", listener: (event: NotificationEvent) => void): void;
}

const sw = self as unknown as ServiceWorkerScope;
const CACHE_PREFIX = "drink-with-me-";
const CACHE = `${CACHE_PREFIX}${__DWM_SW_VERSION__}`;
const scope = (): string => sw.registration.scope;
const absolute = (path: string): string => new URL(path, scope()).href;
const PRECACHE_URLS = (): Set<string> => new Set(__DWM_PRECACHE__.map(absolute));

sw.addEventListener("install", (event) => {
  event.waitUntil(caches.open(CACHE).then((cache) => cache.addAll([...PRECACHE_URLS()])));
});

sw.addEventListener("activate", (event) => {
  event.waitUntil(
    (async () => {
      for (const name of await caches.keys()) {
        if (name.startsWith(CACHE_PREFIX) && name !== CACHE) await caches.delete(name);
      }
      await sw.clients.claim();
    })(),
  );
});

sw.addEventListener("fetch", (event) => {
  const { request } = event;
  if (request.method !== "GET") return;
  const url = new URL(request.url);
  if (url.origin !== self.location.origin || !url.href.startsWith(scope())) return;

  if (request.mode === "navigate") {
    event.respondWith(
      (async () => {
        try {
          const response = await fetch(request);
          // GitHub Pages liefert für App-Pfade (z. B. /einladung/…) die 404.html – das ist die App selbst.
          if (response.ok || response.status === 404) return response;
          return (await caches.match(absolute("index.html"))) ?? response;
        } catch {
          return (await caches.match(absolute("index.html"))) ?? Response.error();
        }
      })(),
    );
    return;
  }

  url.search = "";
  if (PRECACHE_URLS().has(url.href)) {
    event.respondWith(caches.match(url.href).then((cached) => cached ?? fetch(request)));
  }
});

sw.addEventListener("push", (event) => {
  event.waitUntil(
    (async () => {
      const payload = parsePushPayload(event.data?.text());
      const recipient = await readRecipient().catch((): RecipientState => ({ kind: "unknown" }));
      const decision = decidePush(payload, recipient, scope());
      if (decision.kind === "show") {
        await sw.registration.showNotification(decision.plan.title, decision.plan.options);
      } else if (decision.kind === "unsubscribe") {
        // Ausdrücklich abgemeldet: abbestellen, damit keine weiteren Nachrichten kommen. Der Server löscht das Abo
        // beim nächsten Versuch (404/410). Fehlt nur die Nutzer-ID, bleibt das Abo (siehe decidePush).
        const subscription = await sw.registration.pushManager.getSubscription();
        await subscription?.unsubscribe();
      }
    })(),
  );
});

sw.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const data = event.notification.data as { url?: unknown } | null;
  const target = typeof data?.url === "string" && data.url.startsWith(scope()) ? data.url : scope();
  event.waitUntil(
    (async () => {
      const windows = await sw.clients.matchAll({ type: "window", includeUncontrolled: true });
      const existing = windows.find((client) => client.url.startsWith(scope()));
      const navigated = existing ? await existing.navigate(target).catch(() => null) : null;
      if (navigated) {
        await navigated.focus();
        return;
      }
      await sw.clients.openWindow(target);
    })(),
  );
});
