/**
 * Gemeinsamer Mini-Speicher von Seite und Service Worker (eigene IndexedDB, ohne Bibliothek, damit der Service
 * Worker klein bleibt): Die Seite legt hier die ID des angemeldeten Nutzers ab; der Service Worker zeigt eine
 * Push-Nachricht nur an, wenn deren `recipientId` genau dieser ID entspricht.
 *
 * Nach einer **ausdrücklichen** Abmeldung steht hier eine Markierung statt der ID. Nur dann bestellt der Service
 * Worker das Abo ab – fehlt die ID nur (z. B. Speicher frisch, ältere Version), bleibt das Abo bestehen.
 */
export const RECIPIENT_DATABASE = "drink-with-me-push";
const STORE = "kv";
const KEY = "recipientId";
/** Markierung „ausdrücklich abgemeldet“ – ein Objekt, damit sie nie mit einer Nutzer-ID verwechselt wird. */
const SIGNED_OUT = { signedOut: true } as const;

/** Was der Service Worker über den Empfänger weiß. */
export type RecipientState =
  | { readonly kind: "signedIn"; readonly userId: string }
  | { readonly kind: "signedOut" }
  | { readonly kind: "unknown" };

function open(factory: IDBFactory): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = factory.open(RECIPIENT_DATABASE, 1);
    request.onupgradeneeded = () => {
      if (!request.result.objectStoreNames.contains(STORE)) request.result.createObjectStore(STORE);
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error ?? new Error("IndexedDB nicht verfügbar"));
    request.onblocked = () => reject(new Error("IndexedDB blockiert"));
  });
}

function run<T>(factory: IDBFactory, mode: IDBTransactionMode, action: (store: IDBObjectStore) => IDBRequest<T>): Promise<T> {
  return open(factory).then(
    (db) =>
      new Promise<T>((resolve, reject) => {
        const tx = db.transaction(STORE, mode);
        const request = action(tx.objectStore(STORE));
        tx.oncomplete = () => {
          db.close();
          resolve(request.result);
        };
        tx.onerror = () => {
          db.close();
          reject(tx.error ?? new Error("IndexedDB-Fehler"));
        };
        tx.onabort = tx.onerror;
      }),
  );
}

/** Setzt die Nutzer-ID für den Service Worker; `null` = ausdrücklich abgemeldet (Markierung statt ID). */
export function writeRecipientId(id: string | null, factory: IDBFactory = indexedDB): Promise<void> {
  return run<unknown>(factory, "readwrite", (store) => store.put(id === null ? SIGNED_OUT : id, KEY) as IDBRequest<unknown>).then(
    () => undefined,
  );
}

/** Liest den Stand: angemeldet (mit ID), ausdrücklich abgemeldet oder unbekannt. */
export function readRecipient(factory: IDBFactory = indexedDB): Promise<RecipientState> {
  return run(factory, "readonly", (store) => store.get(KEY) as IDBRequest<unknown>).then((value): RecipientState => {
    if (typeof value === "string" && value.length > 0) return { kind: "signedIn", userId: value };
    if (typeof value === "object" && value !== null && (value as { signedOut?: unknown }).signedOut === true) return { kind: "signedOut" };
    return { kind: "unknown" };
  });
}

/** Liest die Nutzer-ID oder `null` (abgemeldet oder unbekannt). */
export function readRecipientId(factory: IDBFactory = indexedDB): Promise<string | null> {
  return readRecipient(factory).then((state) => (state.kind === "signedIn" ? state.userId : null));
}
