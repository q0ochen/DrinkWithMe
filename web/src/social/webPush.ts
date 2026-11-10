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

  async status(): Promise<PushStatus> {
    const registration = await this.environment.registration().catch(() => null);
    const permission = this.environment.permission();
    const supported = registration !== null && permission !== "unsupported" && "pushManager" in registration;
    const subscription = supported ? await registration.pushManager.getSubscription().catch(() => null) : null;
    return {
      supported,
      needsHomeScreen: needsHomeScreenForPush(this.environment.platform),
      permission,
      subscribed: subscription !== null && permission === "granted",
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
    let subscription = await registration.pushManager.getSubscription();
    if (subscription && !this.sameKey(subscription)) {
      await subscription.unsubscribe();
      subscription = null;
    }
    subscription ??= await registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: base64UrlToBytes(this.vapidPublicKey),
    });
    await this.register(subscription);
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

  async sync(): Promise<void> {
    if (!this.vapidPublicKey || !this.isSignedIn() || this.environment.permission() !== "granted") return;
    const registration = await this.environment.registration();
    const subscription = registration ? await registration.pushManager.getSubscription() : null;
    if (subscription) await this.register(subscription);
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
        if (retry.error) throw fromPostgrestError(retry.error, retry.status);
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
