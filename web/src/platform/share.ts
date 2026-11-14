/** Bild teilen: Web Share API mit Dateien, sonst Herunterladen. */
export interface ImageSharer {
  canShareFiles(file: File): boolean;
  /** `false`, wenn der Nutzer das Teilen abgebrochen hat. */
  share(file: File, title: string, text: string): Promise<boolean>;
  download(blob: Blob, fileName: string): void;
}

interface ShareNavigator {
  canShare?(data: ShareData): boolean;
  share?(data: ShareData): Promise<void>;
}

export function createImageSharer(nav: ShareNavigator = navigator as ShareNavigator, doc: Document = document): ImageSharer {
  return {
    canShareFiles(file) {
      try {
        return typeof nav.share === "function" && typeof nav.canShare === "function" && nav.canShare({ files: [file] });
      } catch {
        return false;
      }
    },
    async share(file, title, text) {
      try {
        await nav.share?.({ files: [file], title, text });
        return true;
      } catch (error) {
        if (error instanceof DOMException && error.name === "AbortError") return false;
        throw error;
      }
    },
    download(blob, fileName) {
      const url = URL.createObjectURL(blob);
      const link = doc.createElement("a");
      link.href = url;
      link.download = fileName;
      link.rel = "noopener";
      doc.body.append(link);
      link.click();
      link.remove();
      setTimeout(() => URL.revokeObjectURL(url), 30_000);
    },
  };
}

/** Text in die Zwischenablage (für den Einladungs-Link, wenn Teilen nicht geht). */
export async function copyText(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    return false;
  }
}

/** Link teilen (Einladung) – `null`, wenn der Browser kein Teilen anbietet. */
export async function shareLink(title: string, text: string, url: string): Promise<boolean | null> {
  const nav = navigator as ShareNavigator;
  if (typeof nav.share !== "function") return null;
  try {
    await nav.share({ title, text, url });
    return true;
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") return false;
    return null;
  }
}
