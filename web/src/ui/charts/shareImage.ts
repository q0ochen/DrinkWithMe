import type { ChartModel } from "./chartModel";

/** Inhalt des Bildes „Als Bild teilen“ (nur die eigene Übersicht – nichts davon geht an den Server). */
export interface ShareImageContent {
  readonly dateText: string;
  readonly figures: ReadonlyArray<{ readonly label: string; readonly value: string }>;
  readonly sugar: ChartModel;
  readonly alcohol: ChartModel;
  readonly footer: readonly string[];
  readonly appIconUrl?: string;
}

export const SHARE_IMAGE_WIDTH = 1080;
export const SHARE_IMAGE_HEIGHT = 1350;

const COLORS = {
  background: "#F4F8F9",
  surface: "#FFFFFF",
  border: "#D5E3E6",
  teal: "#12707F",
  text: "#1B2A2E",
  muted: "#4A5B60",
  grid: "#DDE7EA",
  sugar: "#B86E00",
  sugarCurrent: "#8A5200",
  alcohol: "#7A3E9D",
  alcoholCurrent: "#5B2B77",
  reference: "#263238",
  bandLow: "#E3EEF2",
  bandModerate: "#FCEBCB",
  bandHigh: "#F8D7DA",
} as const;

const FONT = 'system-ui, -apple-system, "Segoe UI", Roboto, "Noto Sans", Arial, sans-serif';

function wrap(context: CanvasRenderingContext2D, text: string, maxWidth: number): string[] {
  const words = text.split(/\s+/);
  const lines: string[] = [];
  let line = "";
  for (const word of words) {
    const candidate = line ? `${line} ${word}` : word;
    if (context.measureText(candidate).width > maxWidth && line) {
      lines.push(line);
      line = word;
    } else {
      line = candidate;
    }
  }
  if (line) lines.push(line);
  return lines;
}

function roundedRect(context: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number): void {
  context.beginPath();
  context.moveTo(x + r, y);
  context.arcTo(x + w, y, x + w, y + h, r);
  context.arcTo(x + w, y + h, x, y + h, r);
  context.arcTo(x, y + h, x, y, r);
  context.arcTo(x, y, x + w, y, r);
  context.closePath();
}

function drawChart(context: CanvasRenderingContext2D, model: ChartModel, x: number, y: number, w: number, h: number): void {
  const margin = { top: 56, right: 70, bottom: 46, left: 56 };
  context.fillStyle = COLORS.text;
  context.font = `600 30px ${FONT}`;
  context.fillText(model.kind === "sugar" ? "Zucker aus Getränken pro Tag" : "Alkohol pro Woche", x, y + 34);
  const plotX = x + margin.left;
  const plotY = y + margin.top;
  const plotW = w - margin.left - margin.right;
  const plotH = h - margin.top - margin.bottom;
  const scaleY = (value: number) => plotY + plotH - (Math.min(value, model.max) / model.max) * plotH;

  const bandColors: Record<string, string> = { LOW_RISK: COLORS.bandLow, MODERATE_RISK: COLORS.bandModerate, HIGH_RISK: COLORS.bandHigh };
  for (const band of model.bands) {
    context.fillStyle = bandColors[band.band];
    context.fillRect(plotX, scaleY(band.to), plotW, scaleY(band.from) - scaleY(band.to));
    context.fillStyle = COLORS.muted;
    context.font = `22px ${FONT}`;
    context.fillText(band.label, plotX + 10, scaleY(band.to) + 28);
  }
  context.font = `22px ${FONT}`;
  context.textAlign = "right";
  for (const tick of model.ticks) {
    context.strokeStyle = COLORS.grid;
    context.lineWidth = 2;
    context.beginPath();
    context.moveTo(plotX, scaleY(tick));
    context.lineTo(plotX + plotW, scaleY(tick));
    context.stroke();
    context.fillStyle = COLORS.muted;
    context.fillText(String(tick), plotX - 10, scaleY(tick) + 8);
  }
  context.textAlign = "center";
  const slot = plotW / model.bars.length;
  const barWidth = Math.min(slot * 0.62, 70);
  model.bars.forEach((bar, index) => {
    const center = plotX + slot * index + slot / 2;
    const top = scaleY(bar.value);
    if (bar.value > 0) {
      context.fillStyle =
        model.kind === "sugar" ? (bar.isCurrent ? COLORS.sugarCurrent : COLORS.sugar) : bar.isCurrent ? COLORS.alcoholCurrent : COLORS.alcohol;
      roundedRect(context, center - barWidth / 2, top, barWidth, plotY + plotH - top, 5);
      context.fill();
    }
    const every = model.bars.length > 8 ? 2 : 1;
    if ((model.bars.length - 1 - index) % every === 0) {
      context.fillStyle = bar.isCurrent ? COLORS.text : COLORS.muted;
      context.font = `${bar.isCurrent ? "600 " : ""}21px ${FONT}`;
      context.fillText(bar.shortLabel, center, plotY + plotH + 32);
    }
  });
  context.textAlign = "left";
  for (const line of model.lines) {
    context.strokeStyle = COLORS.reference;
    context.lineWidth = 3;
    context.setLineDash(line.style === "DASHED" ? [12, 9] : []);
    context.beginPath();
    context.moveTo(plotX, scaleY(line.value));
    context.lineTo(plotX + plotW, scaleY(line.value));
    context.stroke();
    context.setLineDash([]);
    context.fillStyle = COLORS.reference;
    context.font = `600 22px ${FONT}`;
    context.fillText(line.shortLabel, plotX + plotW + 10, scaleY(line.value) + 8);
  }
  context.strokeStyle = COLORS.reference;
  context.lineWidth = 2;
  context.beginPath();
  context.moveTo(plotX, plotY + plotH);
  context.lineTo(plotX + plotW, plotY + plotH);
  context.stroke();
}

async function loadImage(url: string): Promise<HTMLImageElement | null> {
  return new Promise((resolve) => {
    const image = new Image();
    image.onload = () => resolve(image);
    image.onerror = () => resolve(null);
    image.src = url;
  });
}

/** Zeichnet die Übersicht auf eine Leinwand (1080 × 1350) und liefert ein PNG. */
export async function renderShareImage(content: ShareImageContent, doc: Document = document): Promise<Blob> {
  const canvas = doc.createElement("canvas");
  canvas.width = SHARE_IMAGE_WIDTH;
  canvas.height = SHARE_IMAGE_HEIGHT;
  const context = canvas.getContext("2d");
  if (!context) throw new Error("Canvas nicht verfügbar");
  const pad = 56;
  const innerWidth = SHARE_IMAGE_WIDTH - pad * 2;

  context.fillStyle = COLORS.background;
  context.fillRect(0, 0, SHARE_IMAGE_WIDTH, SHARE_IMAGE_HEIGHT);
  context.fillStyle = COLORS.teal;
  context.fillRect(0, 0, SHARE_IMAGE_WIDTH, 170);
  const icon = content.appIconUrl ? await loadImage(content.appIconUrl) : null;
  const textX = icon ? pad + 110 : pad;
  if (icon) {
    context.save();
    roundedRect(context, pad, 36, 96, 96, 22);
    context.clip();
    context.drawImage(icon, pad, 36, 96, 96);
    context.restore();
  }
  context.fillStyle = "#FFFFFF";
  context.font = `700 52px ${FONT}`;
  context.fillText("Mein Getränke-Verlauf", textX, 92);
  context.font = `28px ${FONT}`;
  context.fillText(`Drink With Me · ${content.dateText}`, textX, 136);

  // Kennzahlen
  let y = 200;
  const columns = Math.min(content.figures.length, 3);
  const cardWidth = (innerWidth - (columns - 1) * 20) / columns;
  content.figures.slice(0, 3).forEach((figure, index) => {
    const x = pad + index * (cardWidth + 20);
    context.fillStyle = COLORS.surface;
    roundedRect(context, x, y, cardWidth, 130, 20);
    context.fill();
    context.strokeStyle = COLORS.border;
    context.lineWidth = 2;
    context.stroke();
    context.fillStyle = COLORS.muted;
    context.font = `24px ${FONT}`;
    context.fillText(figure.label, x + 24, y + 46);
    context.fillStyle = COLORS.text;
    context.font = `700 44px ${FONT}`;
    context.fillText(figure.value, x + 24, y + 104);
  });
  y += 160;

  for (const model of [content.sugar, content.alcohol]) {
    context.fillStyle = COLORS.surface;
    roundedRect(context, pad, y, innerWidth, 350, 24);
    context.fill();
    context.strokeStyle = COLORS.border;
    context.lineWidth = 2;
    context.stroke();
    drawChart(context, model, pad + 28, y + 12, innerWidth - 56, 326);
    y += 370;
  }

  context.fillStyle = COLORS.muted;
  context.font = `22px ${FONT}`;
  for (const paragraph of content.footer) {
    for (const line of wrap(context, paragraph, innerWidth)) {
      context.fillText(line, pad, y + 6);
      y += 30;
    }
    y += 6;
  }

  return new Promise<Blob>((resolve, reject) => {
    canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error("Bild konnte nicht erzeugt werden"))), "image/png");
  });
}
