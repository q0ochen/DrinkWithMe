/**
 * Gemeinsamer Mini-Speicher von Seite und Service Worker (eigene IndexedDB, ohne Bibliothek, damit der Service
 * Worker klein bleibt): Die Seite legt hier die ID des angemeldeten Nutzers ab; der Service Worker zeigt eine
 * Push-Nachricht nur an, wenn deren `recipientId` genau dieser ID entspricht.
 */
export const RECIPIENT_DATABASE = "drink-with-me-push";
const STORE = "kv";
const KEY = "recipientId";

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

/** Setzt (oder löscht mit `null`) die Nutzer-ID für den Service Worker. */
export function writeRecipientId(id: string | null, factory: IDBFactory = indexedDB): Promise<void> {
  return run<unknown>(factory, "readwrite", (store) => (id === null ? store.delete(KEY) : store.put(id, KEY)) as IDBRequest<unknown>).then(
    () => undefined,
  );
}

/** Liest die Nutzer-ID oder `null`. */
export function readRecipientId(factory: IDBFactory = indexedDB): Promise<string | null> {
  return run(factory, "readonly", (store) => store.get(KEY) as IDBRequest<unknown>).then((value) =>
    typeof value === "string" && value.length > 0 ? value : null,
  );
}
