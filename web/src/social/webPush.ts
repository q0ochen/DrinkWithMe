import type { SupabaseClient } from "@supabase/supabase-js";
import { needsHomeScreenForPush, type PlatformInfo } from "../platform/platform";
import { fromPostgrestError, SocialError } from "./errors";
import type { PushService, PushStatus } from "./types";

/** base64url → Bytes (für `applicationServerKey`). */
export function base64UrlToBytes(value: string): Uint8Array<ArrayBuffer> {
  const padded = value.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(value.length / 4) * 4, "=");
  const binary = atob(padded);
  const bytes = new Uint8Array(new ArrayBuffer(binary.length));
  for (let index = 0; index < binary.length; index++) bytes[index] = binary.charCodeAt(index);
  return bytes;
}

/** Was der Dienst vom Browser braucht – als Schnittstelle, damit er ohne echten Browser testbar ist. */
export interface PushEnvironment {
  readonly platform: PlatformInfo;
  /** `null`, wenn der Browser keinen Service Worker/PushManager hat. */
  registration(): Promise<ServiceWorkerRegistration | null>;
  permission(): NotificationPermission | "unsupported";
  requestPermission(): Promise<NotificationPermission>;
}

export function browserPushEnvironment(platform: PlatformInfo): PushEnvironment {
  const supported = typeof navigator !== "undefined" && "serviceWorker" in navigator && typeof PushManager !== "undefined";
  return {
    platform,
    async registration() {
      if (!supported) return null;
      return navigator.serviceWorker.getRegistration().then((registration) => registration ?? null);
    },
    permission() {
      return typeof Notification === "undefined" ? "unsupported" : Notification.permission;
    },
    requestPermission() {
      return Notification.requestPermission();
    },
  };
}

interface SubscriptionKeys {
  endpoint: string;
  p256dh: string;
  auth: string;
}

function keysOf(subscription: PushSubscription): SubscriptionKeys | null {
  const json = subscription.toJSON();
  const p256dh = json.keys?.p256dh;
  const auth = json.keys?.auth;
  if (!json.endpoint || !p256dh || !auth) return null;
  return { endpoint: json.endpoint, p256dh, auth };
}

/**
 * {@link PushService}: abonniert mit `applicationServerKey = VITE_VAPID_PUBLIC_KEY` und speichert
 * `endpoint/p256dh/auth` in `push_subscriptions` (`platform = 'web'`). Kein UPDATE auf dem Server: zum
 * Erneuern wird gelöscht und neu angelegt.
 */
export class WebPushService implements PushService {
  constructor(
    private readonly client: SupabaseClient,
    private readonly environment: PushEnvironment,
    private readonly vapidPublicKey: string | null,
    private readonly isSignedIn: () => boolean,
  ) {}

  /**
   * „Eingeschaltet“ heißt: Browser-Abo **und** Zeile auf dem Server. Fehlt die Zeile (z. B. weil das Speichern
   * scheiterte), ist es aus. Ist der Server nicht erreichbar, zählt das Browser-Abo.
   */
  async status(): Promise<PushStatus> {
    const registration = await this.environment.registration().catch(() => null);
    const permission = this.environment.permission();
    const supported = registration !== null && permission !== "unsupported" && "pushManager" in registration;
    const subscription = supported ? await registration.pushManager.getSubscription().catch(() => null) : null;
    let subscribed = subscription !== null && permission === "granted";
    if (subscribed && subscription && this.isSignedIn()) {
      const onServer = await this.hasServerRow(subscription).catch(() => null);
      if (onServer === false) subscribed = false;
    }
    return {
      supported,
      needsHomeScreen: needsHomeScreenForPush(this.environment.platform),
      permission,
      subscribed,
      configured: this.vapidPublicKey !== null,
    };
  }

  async enable(): Promise<void> {
    if (!this.vapidPublicKey) throw new SocialError("not_configured");
    if (!this.isSignedIn()) throw new SocialError("not_authenticated");
    const registration = await this.environment.registration();
    if (!registration || this.environment.permission() === "unsupported") throw new SocialError("push_unsupported");
    const permission = await this.environment.requestPermission();
    if (permission !== "granted") throw new SocialError("push_denied");
    await this.subscribeAndRegister(registration, this.vapidPublicKey);
  }

  async disable(): Promise<void> {
    const registration = await this.environment.registration().catch(() => null);
    const subscription = registration ? await registration.pushManager.getSubscription() : null;
    if (!subscription) return;
    const keys = keysOf(subscription);
    if (keys && this.isSignedIn()) {
      const result = await this.client.from("push_subscriptions").delete().eq("endpoint", keys.endpoint);
      if (result.error) throw fromPostgrestError(result.error, result.status);
    }
    await subscription.unsubscribe();
  }

  async sync(wanted: boolean): Promise<void> {
    if (!this.vapidPublicKey || !this.isSignedIn() || this.environment.permission() !== "granted") return;
    const registration = await this.environment.registration();
    if (!registration) return;
    const subscription = await registration.pushManager.getSubscription();
    // Ohne Browser-Abo nur neu abonnieren, wenn der Nutzer Benachrichtigungen eingeschaltet hatte (z. B. hat der
    // Browser das Abo verworfen). Die Berechtigung ist schon erteilt – es erscheint keine Abfrage.
    if (subscription || wanted) await this.subscribeAndRegister(registration, this.vapidPublicKey);
  }

  /** Vorhandenes Abo (mit passendem Schlüssel) verwenden oder neu abonnieren; ein **neues** Abo bei Fehlern zurücknehmen. */
  private async subscribeAndRegister(registration: ServiceWorkerRegistration, vapidPublicKey: string): Promise<void> {
    let subscription = await registration.pushManager.getSubscription();
    if (subscription && !this.sameKey(subscription)) {
      await subscription.unsubscribe();
      subscription = null;
    }
    const created = subscription === null;
    subscription ??= await registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: base64UrlToBytes(vapidPublicKey),
    });
    try {
      await this.register(subscription);
    } catch (error) {
      // Sonst bliebe ein Browser-Abo ohne Zeile auf dem Server (die Einstellungen zeigten fälschlich „Eingeschaltet“).
      if (created) await subscription.unsubscribe().catch(() => false);
      throw error;
    }
  }

  private async hasServerRow(subscription: PushSubscription): Promise<boolean> {
    const keys = keysOf(subscription);
    if (!keys) return false;
    // Ohne Wiederholungen: Die Anzeige soll offline sofort auf das Browser-Abo zurückfallen.
    const result = await this.client.from("push_subscriptions").select("endpoint").eq("endpoint", keys.endpoint).limit(1).retry(false);
    if (result.error) throw fromPostgrestError(result.error, result.status);
    return Array.isArray(result.data) && result.data.length > 0;
  }

  /** Abo für den angemeldeten Nutzer speichern (vorher eigenes gleiches Abo löschen – kein UPDATE). */
  private async register(subscription: PushSubscription): Promise<void> {
    const keys = keysOf(subscription);
    if (!keys) throw new SocialError("push_unsupported");
    const removed = await this.client.from("push_subscriptions").delete().eq("endpoint", keys.endpoint);
    if (removed.error) throw fromPostgrestError(removed.error, removed.status);
    const inserted = await this.client
      .from("push_subscriptions")
      .insert({ endpoint: keys.endpoint, p256dh: keys.p256dh, auth: keys.auth, platform: "web" });
    if (inserted.error) {
      // Gehört der Endpoint noch einem anderen Konto (Gerät gewechselt ohne Abmelden), neu abonnieren.
      if (inserted.error.code === "23505" && this.vapidPublicKey) {
        await subscription.unsubscribe();
        const registration = await this.environment.registration();
        if (!registration) throw new SocialError("push_unsupported");
        const fresh = await registration.pushManager.subscribe({
          userVisibleOnly: true,
          applicationServerKey: base64UrlToBytes(this.vapidPublicKey),
        });
        const freshKeys = keysOf(fresh);
        if (!freshKeys) throw new SocialError("push_unsupported");
        const retry = await this.client
          .from("push_subscriptions")
          .insert({ endpoint: freshKeys.endpoint, p256dh: freshKeys.p256dh, auth: freshKeys.auth, platform: "web" });
        if (retry.error) {
          await fresh.unsubscribe().catch(() => false);
          throw fromPostgrestError(retry.error, retry.status);
        }
        return;
      }
      throw fromPostgrestError(inserted.error, inserted.status);
    }
  }

  private sameKey(subscription: PushSubscription): boolean {
    const key = subscription.options?.applicationServerKey;
    if (!key || !this.vapidPublicKey) return true;
    const expected = base64UrlToBytes(this.vapidPublicKey);
    const actual = new Uint8Array(key);
    return actual.length === expected.length && actual.every((byte, index) => byte === expected[index]);
  }
}
