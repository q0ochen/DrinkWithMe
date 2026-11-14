/** Liefert „jetzt“ (Millisekunden seit 1970, UTC). Als Schnittstelle, damit alles deterministisch testbar ist. */
export interface Clock {
  now(): number;
}

export const systemClock: Clock = { now: () => Date.now() };

/** Erzeugt eindeutige IDs (wie `IdGenerator` im Kotlin-Kern). */
export interface IdGenerator {
  newId(): string;
}

/** Zufällige UUIDs (Version 4) – Standard für eigene Bar-Getränke und Trinkeinträge. */
export const randomIdGenerator: IdGenerator = {
  newId: () => crypto.randomUUID(),
};
