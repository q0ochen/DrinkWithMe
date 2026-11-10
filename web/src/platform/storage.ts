/**
 * Bittet den Browser, den Website-Speicher (IndexedDB mit Bar und Verlauf) nicht bei Platzmangel zu räumen
 * (`navigator.storage.persist()`). Chrome entscheidet ohne Rückfrage, Firefox fragt einmal, Safari kann zustimmen.
 * Gegen das automatische Löschen nach 7 Tagen ohne Besuch in Safari hilft das nicht – dafür gibt es die Sicherung.
 */
export interface PersistentStorage {
  /** `true`, wenn der Speicher (jetzt) dauerhaft ist. Wirft nie. */
  request(): Promise<boolean>;
}

interface StorageManagerLike {
  persisted?(): Promise<boolean>;
  persist?(): Promise<boolean>;
}

export function createPersistentStorage(nav: { readonly storage?: StorageManagerLike } = navigator): PersistentStorage {
  return {
    async request() {
      try {
        const storage = nav.storage;
        if (!storage?.persist) return false;
        if (await storage.persisted?.()) return true;
        return (await storage.persist()) === true;
      } catch {
        return false;
      }
    },
  };
}
