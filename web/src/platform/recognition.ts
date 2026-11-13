/** Optionale Spracheingabe über `SpeechRecognition` des Browsers (nur wenn vorhanden). */
export interface SpeechRecognizer {
  /** Hört einmal zu und liefert bis zu 5 Erkennungsergebnisse, das wahrscheinlichste zuerst. */
  listen(): Promise<string[]>;
  abort(): void;
}

export class RecognitionError extends Error {
  constructor(readonly reason: "not-allowed" | "unavailable" | "failed") {
    super(
      reason === "not-allowed"
        ? "Das Mikrofon ist für diese Seite nicht erlaubt. Du kannst es in den Einstellungen des Browsers erlauben."
        : reason === "unavailable"
          ? "Die Spracherkennung ist gerade nicht erreichbar."
          : "Die Spracherkennung hat nicht geklappt. Bitte versuche es noch einmal.",
    );
    this.name = "RecognitionError";
  }
}

interface RecognitionAlternative {
  readonly transcript: string;
}
interface RecognitionResultEvent {
  readonly results: ArrayLike<ArrayLike<RecognitionAlternative>>;
}
interface RecognitionErrorEvent {
  readonly error: string;
}
interface RecognitionLike {
  lang: string;
  maxAlternatives: number;
  interimResults: boolean;
  continuous: boolean;
  onresult: ((event: RecognitionResultEvent) => void) | null;
  onerror: ((event: RecognitionErrorEvent) => void) | null;
  onend: (() => void) | null;
  start(): void;
  abort(): void;
}
type RecognitionConstructor = new () => RecognitionLike;

export function createSpeechRecognizer(scope: Record<string, unknown> = globalThis as unknown as Record<string, unknown>): SpeechRecognizer | null {
  const Constructor = (scope.SpeechRecognition ?? scope.webkitSpeechRecognition) as RecognitionConstructor | undefined;
  if (typeof Constructor !== "function") return null;
  let active: RecognitionLike | null = null;
  return {
    listen() {
      active?.abort();
      const recognition = new Constructor();
      active = recognition;
      recognition.lang = "de-DE";
      recognition.maxAlternatives = 5;
      recognition.interimResults = false;
      recognition.continuous = false;
      return new Promise<string[]>((resolve, reject) => {
        let results: string[] = [];
        recognition.onresult = (event) => {
          const first = event.results[0];
          results = first ? Array.from({ length: first.length }, (_, index) => first[index].transcript).filter(Boolean) : [];
        };
        recognition.onerror = (event) => {
          if (event.error === "no-speech" || event.error === "aborted") return;
          reject(new RecognitionError(event.error === "not-allowed" || event.error === "service-not-allowed" ? "not-allowed" : event.error === "network" ? "unavailable" : "failed"));
        };
        recognition.onend = () => {
          if (active === recognition) active = null;
          resolve(results);
        };
        recognition.start();
      });
    },
    abort() {
      active?.abort();
      active = null;
    },
  };
}
