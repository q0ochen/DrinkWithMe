import type { RecipientState } from "./recipientStore";

/**
 * Inhalt der Push-Nachrichten von `notify-friends` (siehe supabase/README.md):
 * `{ type: "drink", eventId, authorId, recipientId, title, body }`.
 */
export interface DrinkPushPayload {
  readonly type: "drink";
  readonly eventId: string;
  readonly authorId: string;
  readonly recipientId: string;
  readonly title: string;
  readonly body: string;
}

const MAX_TEXT = 200;

/** Liest eine Push-Nachricht; `null` bei unbekanntem Typ oder unvollständigem Inhalt. */
export function parsePushPayload(text: string | null | undefined): DrinkPushPayload | null {
  if (!text) return null;
  let value: unknown;
  try {
    value = JSON.parse(text);
  } catch {
    return null;
  }
  if (typeof value !== "object" || value === null) return null;
  const data = value as Record<string, unknown>;
  const fields = ["eventId", "authorId", "recipientId", "title", "body"] as const;
  if (data.type !== "drink" || !fields.every((field) => typeof data[field] === "string" && (data[field] as string).length > 0)) {
    return null;
  }
  return {
    type: "drink",
    eventId: data.eventId as string,
    authorId: data.authorId as string,
    recipientId: data.recipientId as string,
    title: (data.title as string).slice(0, MAX_TEXT),
    body: (data.body as string).slice(0, MAX_TEXT),
  };
}

/** Adresse des Ereignis-Bildschirms innerhalb der App (Scope endet immer mit „/“). */
export function eventUrl(scope: string, eventId: string): string {
  return `${scope}ereignis/${encodeURIComponent(eventId)}`;
}

export interface NotificationPlan {
  readonly title: string;
  readonly options: {
    readonly body: string;
    readonly tag: string;
    readonly icon: string;
    readonly badge: string;
    readonly lang: string;
    readonly data: { readonly url: string; readonly eventId: string };
  };
}

/**
 * Entscheidet, ob und wie eine Nachricht angezeigt wird: **nur**, wenn `recipientId` dem angemeldeten Nutzer
 * entspricht (gespeichert von der Seite). Sonst `null` (nichts anzeigen).
 */
export function planNotification(payload: DrinkPushPayload | null, signedInUserId: string | null, scope: string): NotificationPlan | null {
  if (payload === null || signedInUserId === null || payload.recipientId !== signedInUserId) return null;
  return {
    title: payload.title,
    options: {
      body: payload.body,
      tag: `drink-${payload.eventId}`,
      icon: `${scope}icons/app-192.png`,
      badge: `${scope}icons/badge-96.png`,
      lang: "de-DE",
      data: { url: eventUrl(scope, payload.eventId), eventId: payload.eventId },
    },
  };
}

/** Was der Service Worker mit einer Push-Nachricht tut. */
export type PushDecision =
  | { readonly kind: "show"; readonly plan: NotificationPlan }
  | { readonly kind: "unsubscribe" }
  | { readonly kind: "ignore" };

/**
 * Anzeigen nur für den angemeldeten Empfänger. Abbestellen **nur** nach ausdrücklicher Abmeldung – nicht, weil die
 * Nutzer-ID gerade fehlt oder nicht lesbar ist (z. B. nach einem vorübergehenden Fehler beim Erneuern der Sitzung).
 */
export function decidePush(payload: DrinkPushPayload | null, recipient: RecipientState, scope: string): PushDecision {
  if (payload === null) return { kind: "ignore" };
  if (recipient.kind === "signedOut") return { kind: "unsubscribe" };
  const plan = planNotification(payload, recipient.kind === "signedIn" ? recipient.userId : null, scope);
  return plan ? { kind: "show", plan } : { kind: "ignore" };
}
