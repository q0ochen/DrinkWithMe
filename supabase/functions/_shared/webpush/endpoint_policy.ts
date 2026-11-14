/**
 * Schutz vor SSRF beim Push-Versand. Endpoints in public.push_subscriptions legen die Nutzer selbst an; die Edge
 * Function darf damit nur öffentliche https-Dienste erreichen – keine internen Adressen und keine fremden Ports.
 *
 * Zwei Stufen:
 * 1. {@link pushEndpointHost} prüft die URL ohne Netz: https, keine Zugangsdaten, nur Port 443, ein Hostname aus
 *    mindestens zwei nicht leeren Labels, keine IP-Literale, keine lokalen/internen Namen. Ein Punkt am Ende
 *    („localhost.“) wird vorher entfernt, damit er die Namensliste nicht umgeht.
 * 2. {@link DnsPushHostVerifier} löst den Hostnamen auf (A und AAAA) und lehnt ihn ab, sobald auch nur eine Adresse
 *    nicht öffentlich ist (Loopback, privat, Link-Local, CGNAT, ULA, Multicast, Dokumentation, …).
 *
 * Restrisiko: fetch löst den Namen beim Verbindungsaufbau noch einmal selbst auf (DNS-Rebinding zwischen Prüfung
 * und Versand). Weil nur https mit Zertifikatsprüfung erlaubt ist, scheitert ein so umgeleiteter Versand an einem
 * internen Dienst aber am TLS-Handshake – er hat kein öffentlich gültiges Zertifikat für genau diesen Namen.
 */

/** Namensendungen, die nie öffentlich sind (RFC 6761/6762/8375 und übliche interne Zonen). */
const INTERNAL_NAME_SUFFIX =
  /(^|\.)(localhost|local|localdomain|internal|lan|home|intranet|corp|private|arpa|test|invalid)$/;

/**
 * Bereinigter Hostname eines zulässigen Push-Endpoints (klein geschrieben, ohne Punkt am Ende) oder `null`, wenn der
 * Endpoint schon ohne DNS-Abfrage unzulässig ist.
 */
export function pushEndpointHost(endpoint: string): string | null {
  let url: URL;
  try {
    url = new URL(endpoint);
  } catch {
    return null;
  }
  if (url.protocol !== "https:" || url.username !== "" || url.password !== "") {
    return null;
  }
  // Port 443 steht nach dem URL-Standard als leerer String; jeder andere Port ist verboten.
  if (url.port !== "") {
    return null;
  }
  let host = url.hostname.toLowerCase();
  if (host.endsWith(".")) {
    host = host.slice(0, -1);
  }
  if (host.startsWith("[") || /^[0-9.]+$/.test(host)) {
    return null; // IP-Adressen (v4/v6) statt Hostnamen
  }
  const labels = host.split(".");
  if (labels.length < 2 || labels.some((label) => label === "")) {
    return null; // einteilige Namen („intranet“) und leere Labels („a..b“, „localhost..“)
  }
  return INTERNAL_NAME_SUFFIX.test(host) ? null : host;
}

/** Prüfung ohne Netz: `true`, wenn der Endpoint der Form nach ein öffentlicher https-Dienst auf Port 443 ist. */
export function isAllowedPushEndpoint(endpoint: string): boolean {
  return pushEndpointHost(endpoint) !== null;
}

// ---------------------------------------------------------------------------------------------------------------
// IP-Adressen
// ---------------------------------------------------------------------------------------------------------------

/** IPv4 in Punktschreibweise → 32-bit-Zahl, sonst `null`. */
function parseIpv4(address: string): number | null {
  const match = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/.exec(address);
  if (!match) return null;
  const parts = match.slice(1).map(Number);
  if (parts.some((part) => part > 255)) return null;
  return ((parts[0] << 24) >>> 0) + (parts[1] << 16) + (parts[2] << 8) + parts[3];
}

/** Nicht öffentliche IPv4-Bereiche (RFC 6890 und Nachfolger) als [Netzadresse, Maske]. */
const NON_PUBLIC_IPV4: ReadonlyArray<readonly [number, number]> = ([
  ["0.0.0.0", 8], // „dieses Netz“
  ["10.0.0.0", 8], // privat
  ["100.64.0.0", 10], // CGNAT
  ["127.0.0.0", 8], // Loopback
  ["169.254.0.0", 16], // Link-Local (u. a. Cloud-Metadaten)
  ["172.16.0.0", 12], // privat
  ["192.0.0.0", 24], // IETF-Protokollzuweisungen
  ["192.0.2.0", 24], // Dokumentation
  ["192.88.99.0", 24], // 6to4-Relay (veraltet)
  ["192.168.0.0", 16], // privat
  ["198.18.0.0", 15], // Benchmarking
  ["198.51.100.0", 24], // Dokumentation
  ["203.0.113.0", 24], // Dokumentation
  ["224.0.0.0", 4], // Multicast
  ["240.0.0.0", 4], // reserviert und Broadcast
] as const).map(([network, prefix]) => {
  const mask = (~0 << (32 - prefix)) >>> 0;
  return [(parseIpv4(network)! & mask) >>> 0, mask] as const;
});

function isPublicIpv4(value: number): boolean {
  return !NON_PUBLIC_IPV4.some(([network, mask]) => ((value & mask) >>> 0) === network);
}

/** IPv6 (auch mit „::“ und IPv4-Ende) → 8 Gruppen à 16 bit, sonst `null`. Zonen („%eth0“) gelten als ungültig. */
function parseIpv6(address: string): number[] | null {
  if (!/^[0-9a-fA-F:.]+$/.test(address)) return null;
  let text = address;
  let tail: number[] = [];
  if (text.includes(".")) {
    // Die letzten 32 bit dürfen als IPv4-Adresse geschrieben sein (z. B. ::ffff:127.0.0.1).
    const lastColon = text.lastIndexOf(":");
    const ipv4 = lastColon < 0 ? null : parseIpv4(text.slice(lastColon + 1));
    if (ipv4 === null) return null;
    tail = [ipv4 >>> 16, ipv4 & 0xffff];
    text = text.slice(0, lastColon + 1);
    if (!text.endsWith("::")) text = text.slice(0, -1);
  }
  const halves = text.split("::");
  if (halves.length > 2) return null;
  const groupsOf = (part: string): number[] | null => {
    if (part === "") return [];
    const groups = part.split(":");
    return groups.every((group) => /^[0-9a-fA-F]{1,4}$/.test(group)) ? groups.map((g) => parseInt(g, 16)) : null;
  };
  const head = groupsOf(halves[0]);
  const rest = halves.length === 2 ? groupsOf(halves[1]) : [];
  if (head === null || rest === null) return null;
  const explicit = head.length + rest.length + tail.length;
  if (halves.length === 2) {
    return explicit > 7 ? null : [...head, ...new Array<number>(8 - explicit).fill(0), ...rest, ...tail];
  }
  return explicit === 8 ? [...head, ...tail] : null;
}

/**
 * Öffentlich ist nur globaler Unicast (2000::/3) außerhalb der Sonderbereiche. Damit sind Loopback, ::, IPv4-mapped,
 * NAT64, ULA (fc00::/7), Link-Local (fe80::/10), Site-Local und Multicast (ff00::/8) automatisch ausgeschlossen.
 */
function isPublicIpv6(groups: number[]): boolean {
  if ((groups[0] & 0xe000) !== 0x2000) return false;
  if (groups[0] === 0x2001 && groups[1] < 0x0200) return false; // 2001::/23 (u. a. Teredo, Benchmarking)
  if (groups[0] === 0x2001 && groups[1] === 0x0db8) return false; // 2001:db8::/32 Dokumentation
  if (groups[0] === 0x2002) return false; // 6to4 (bettet beliebige IPv4-Adressen ein)
  if (groups[0] === 0x3fff && groups[1] < 0x1000) return false; // 3fff::/20 Dokumentation
  return true;
}

/** `true` nur für öffentlich erreichbare Unicast-Adressen (IPv4 oder IPv6); alles Unbekannte gilt als nicht öffentlich. */
export function isPublicIpAddress(address: string): boolean {
  const ipv4 = parseIpv4(address);
  if (ipv4 !== null) return isPublicIpv4(ipv4);
  const ipv6 = parseIpv6(address);
  return ipv6 !== null && isPublicIpv6(ipv6);
}

// ---------------------------------------------------------------------------------------------------------------
// DNS
// ---------------------------------------------------------------------------------------------------------------

/** Eine DNS-Abfrage (wie `Deno.resolveDns`); wirft `Deno.errors.NotFound`, wenn es keine Einträge gibt. */
export type DnsLookup = (
  hostname: string,
  recordType: "A" | "AAAA",
  options: { signal: AbortSignal },
) => Promise<string[]>;

/** Port: Hostname → alle IP-Adressen. */
export interface HostResolver {
  resolve(hostname: string): Promise<string[]>;
}

/**
 * Fragt A und AAAA gleichzeitig ab. „Keine Einträge“ für eine Art ist normal (z. B. kein IPv6); jeder andere Fehler
 * bricht ab, weil sonst eine Adressart ungeprüft bliebe, die fetch später benutzen könnte.
 */
export class ResolvingHostResolver implements HostResolver {
  constructor(private readonly lookup: DnsLookup, private readonly timeoutMs = 5_000) {}

  async resolve(hostname: string): Promise<string[]> {
    const signal = AbortSignal.timeout(this.timeoutMs);
    const answers = await Promise.allSettled(
      (["A", "AAAA"] as const).map((recordType) => this.lookup(hostname, recordType, { signal })),
    );
    const addresses: string[] = [];
    for (const answer of answers) {
      if (answer.status === "fulfilled") {
        addresses.push(...answer.value);
      } else if (!(answer.reason instanceof Deno.errors.NotFound)) {
        throw answer.reason;
      }
    }
    return addresses;
  }
}

/** Ergebnis der DNS-Prüfung. `unresolved` ist vorübergehend: nicht senden, Abo aber behalten. */
export type HostVerdict =
  | { kind: "allowed" }
  | { kind: "not_allowed" }
  | { kind: "unresolved"; reason: "dns_no_address" | "dns_error" };

/** Port: Darf an diesen (der Form nach schon geprüften) Hostnamen gesendet werden? */
export interface PushHostVerifier {
  verify(hostname: string): Promise<HostVerdict>;
}

/** Erlaubt einen Host nur, wenn er auflösbar ist und ALLE seine Adressen öffentlich sind. */
export class DnsPushHostVerifier implements PushHostVerifier {
  constructor(private readonly resolver: HostResolver) {}

  async verify(hostname: string): Promise<HostVerdict> {
    let addresses: string[];
    try {
      addresses = await this.resolver.resolve(hostname);
    } catch {
      return { kind: "unresolved", reason: "dns_error" };
    }
    if (addresses.length === 0) {
      return { kind: "unresolved", reason: "dns_no_address" };
    }
    return addresses.every(isPublicIpAddress) ? { kind: "allowed" } : { kind: "not_allowed" };
  }
}
