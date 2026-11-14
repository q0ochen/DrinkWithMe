/** Vorlesen über die Sprachausgabe des Browsers (`speechSynthesis`, de-DE). */
export interface Speaker {
  /** Der Browser kann vorlesen. */
  readonly available: boolean;
  /** Eine deutsche Stimme ist installiert (wartet kurz, bis der Browser die Stimmen kennt). */
  hasGermanVoice(): Promise<boolean>;
  /** Spricht sofort (muss auf iPhone direkt aus einem Tipp heraus aufgerufen werden). */
  speak(text: string): boolean;
  cancel(): void;
}

interface SynthesisLike {
  getVoices(): SpeechSynthesisVoice[];
  speak(utterance: SpeechSynthesisUtterance): void;
  cancel(): void;
  addEventListener?(type: "voiceschanged", listener: () => void): void;
}

/** Bevorzugt de-DE, sonst jede deutsche Stimme (de-AT, de-CH …). */
export function pickGermanVoice(voices: readonly SpeechSynthesisVoice[]): SpeechSynthesisVoice | null {
  const german = voices.filter((voice) => voice.lang.toLowerCase().replace("_", "-").startsWith("de"));
  return german.find((voice) => voice.lang.toLowerCase().replace("_", "-") === "de-de" && voice.localService) ??
    german.find((voice) => voice.lang.toLowerCase().replace("_", "-") === "de-de") ??
    german[0] ??
    null;
}

export function createBrowserSpeaker(
  synthesis: SynthesisLike | undefined = typeof speechSynthesis === "undefined" ? undefined : speechSynthesis,
  Utterance: typeof SpeechSynthesisUtterance | undefined = typeof SpeechSynthesisUtterance === "undefined" ? undefined : SpeechSynthesisUtterance,
): Speaker {
  if (!synthesis || !Utterance) {
    return { available: false, hasGermanVoice: async () => false, speak: () => false, cancel: () => {} };
  }
  let voice: SpeechSynthesisVoice | null = pickGermanVoice(synthesis.getVoices());
  const voicesReady = new Promise<void>((resolve) => {
    if (synthesis.getVoices().length > 0) {
      resolve();
      return;
    }
    synthesis.addEventListener?.("voiceschanged", () => resolve());
    setTimeout(resolve, 1500);
  }).then(() => {
    voice = pickGermanVoice(synthesis.getVoices());
  });
  return {
    available: true,
    async hasGermanVoice() {
      await voicesReady;
      return voice !== null;
    },
    speak(text: string) {
      const trimmed = text.trim();
      if (!trimmed) return false;
      synthesis.cancel();
      const utterance = new Utterance(trimmed);
      utterance.lang = "de-DE";
      if (voice) utterance.voice = voice;
      utterance.rate = 0.95;
      synthesis.speak(utterance);
      return true;
    },
    cancel() {
      synthesis.cancel();
    },
  };
}
