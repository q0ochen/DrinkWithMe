/**
 * Versand einer verschlüsselten Push-Nachricht an einen Push-Dienst (RFC 8030) – für UnifiedPush (z. B. ntfy)
 * und Web Push im Browser gleichermaßen, beide mit VAPID (RFC 8292) und aes128gcm (RFC 8291).
 */

import { decodeBase64Url } from "../bytes.ts";
import type { Clock } from "../clock.ts";
import { encryptAes128Gcm, type EphemeralSource, PushEncryptionError, randomEphemeralSource } from "./encryption.ts";
import { pushEndpointHost, type PushHostVerifier } from "./endpoint_policy.ts";
import { VapidHeaderFactory } from "./vapid.ts";
import type { VapidCredentials } from "./vapid.ts";

/** Push-Abo wie in public.push_subscriptions gespeichert. */
export interface PushTarget {
  endpoint: string;
  /** Öffentlicher Schlüssel des Empfängers (base64url). */
  p256dh: string;
  /** Authentifizierungsgeheimnis des Empfängers (base64url). */
  auth: string;
}

/** Versandoptionen nach RFC 8030. */
export interface PushOptions {
  /** Wie lange der Push-Dienst die Nachricht aufbewahrt, falls das Gerät offline ist (Sekunden). */
  ttlSeconds: number;
  urgency?: "very-low" | "low" | "normal" | "high";
}

/**
 * Ergebnis eines Versands:
 * - `delivered`: vom Push-Dienst angenommen (2xx),
 * - `gone`: Abo existiert nicht mehr (404/410) → Abo löschen,
 * - `invalid`: Abo ist unbrauchbar (Schlüssel/Endpoint ungültig) → Abo löschen,
 * - `failed`: vorübergehender oder unbekannter Fehler → Abo behalten.
 */
export type PushResult =
  | { kind: "delivered"; status: number }
  | { kind: "gone"; status: number }
  | { kind: "invalid"; reason: string }
  | { kind: "failed"; status?: number; reason: string };

/** Port: verschickt eine Push-Nachricht. */
export interface PushSender {
  send(target: PushTarget, payload: Uint8Array, options: PushOptions): Promise<PushResult>;
}

/** Abhängigkeiten des WebPushSender (alles injiziert, damit er sich ohne Netz testen lässt). */
export interface WebPushSenderDeps {
  credentials: VapidCredentials;
  fetch: typeof fetch;
  clock: Clock;
  /** DNS-Prüfung des Ziel-Hosts (SSRF-Schutz, siehe endpoint_policy.ts). */
  hosts: PushHostVerifier;
  ephemeral?: EphemeralSource;
  /** Zeitlimit pro Anfrage an den Push-Dienst (Millisekunden). */
  timeoutMs?: number;
}

/** Verschickt Nachrichten per HTTP POST an den Endpoint des Push-Abos. */
export class WebPushSender implements PushSender {
  readonly #vapid: VapidHeaderFactory;

  constructor(private readonly deps: WebPushSenderDeps) {
    this.#vapid = new VapidHeaderFactory(deps.credentials);
  }

  /**
   * Reihenfolge: erst die Prüfungen ohne Netz (Form des Endpoints, Schlüssel des Empfängers), dann die DNS-Prüfung
   * des Hosts, erst danach der Versand. Unzulässige Endpoints und Schlüssel → `invalid` (Abo wird gelöscht);
   * eine DNS-Störung → `failed` (Abo bleibt, nächster Versuch beim nächsten Getränk).
   */
  async send(target: PushTarget, payload: Uint8Array, options: PushOptions): Promise<PushResult> {
    const host = pushEndpointHost(target.endpoint);
    if (host === null) {
      return { kind: "invalid", reason: "endpoint_not_allowed" };
    }
    let body: Uint8Array;
    try {
      body = await encryptAes128Gcm(
        payload,
        { publicKey: decodeBase64Url(target.p256dh), authSecret: decodeBase64Url(target.auth) },
        this.deps.ephemeral ?? randomEphemeralSource,
      );
    } catch (error) {
      if (error instanceof PushEncryptionError || error instanceof TypeError) {
        return { kind: "invalid", reason: "receiver_keys_invalid" };
      }
      throw error;
    }

    const verdict = await this.deps.hosts.verify(host);
    if (verdict.kind === "not_allowed") {
      return { kind: "invalid", reason: "endpoint_not_allowed" };
    }
    if (verdict.kind === "unresolved") {
      return { kind: "failed", reason: verdict.reason };
    }

    const headers = new Headers({
      "Authorization": await this.#vapid.authorizationFor(target.endpoint, this.deps.clock.now()),
      "Content-Encoding": "aes128gcm",
      "Content-Type": "application/octet-stream",
      "TTL": String(Math.max(0, Math.floor(options.ttlSeconds))),
      "Urgency": options.urgency ?? "normal",
    });

    let response: Response;
    try {
      response = await this.deps.fetch(target.endpoint, {
        method: "POST",
        headers,
        body: body as BodyInit,
        redirect: "manual",
        signal: AbortSignal.timeout(this.deps.timeoutMs ?? 10_000),
      });
    } catch (error) {
      return { kind: "failed", reason: error instanceof Error ? error.name : "network_error" };
    }
    // Antwortkörper verwerfen, damit die Verbindung freigegeben wird.
    await response.body?.cancel();

    if (response.status >= 200 && response.status < 300) {
      return { kind: "delivered", status: response.status };
    }
    if (response.status === 404 || response.status === 410) {
      return { kind: "gone", status: response.status };
    }
    return { kind: "failed", status: response.status, reason: `http_${response.status}` };
  }
}
