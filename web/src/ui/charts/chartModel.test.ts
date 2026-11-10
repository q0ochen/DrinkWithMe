import { describe, expect, it } from "vitest";
import { LocalDate, ianaTimeZone } from "../../core/time/localDate";
import { dayHeading, formatGrams, formatPercentChange, formatRelative, initials } from "../format";
import { alcoholChartModel, niceScale, sugarChartModel } from "./chartModel";

const today = LocalDate.of(2026, 11, 10); // Dienstag

describe("Diagramm-Modelle (gemeinsam für SVG und Bild)", () => {
  it("runde Achsen mit höchstens 5 Schritten", () => {
    expect(niceScale(55)).toEqual({ max: 60, ticks: [0, 20, 40, 60] });
    expect(niceScale(93.15)).toEqual({ max: 100, ticks: [0, 20, 40, 60, 80, 100] });
    expect(niceScale(0)).toEqual({ max: 5, ticks: [0, 5] });
    expect(niceScale(420).ticks.length).toBeLessThanOrEqual(6);
  });

  it("Zucker: 50-g-Linie durchgezogen, 25-g-Linie gestrichelt, immer sichtbar", () => {
    const days = Array.from({ length: 14 }, (_, index) => ({ date: today.minusDays(13 - index), grams: index === 13 ? 34.98 : 0 }));
    const model = sugarChartModel(days, today);
    expect(model.lines.map((line) => [line.value, line.style, line.shortLabel])).toEqual([
      [50, "SOLID", "50 g"],
      [25, "DASHED", "25 g"],
    ]);
    expect(model.max).toBeGreaterThanOrEqual(55);
    expect(model.bars.at(-1)).toMatchObject({ isCurrent: true, valueText: "35 g", label: "Dienstag, 10. November (heute)" });
    expect(model.summary).toBe("Zucker aus Getränken in den letzten 14 Tagen: heute 35 g, höchster Tag 35 g, 0 Tage über 50 g.");
    expect(model.notes).toContain("Getränke sind nur ein Teil davon.");
  });

  it("Alkohol: DGE-Bänder ausblendbar, Pflichtsatz immer dabei", () => {
    const weeks = Array.from({ length: 8 }, (_, index) => ({ weekStart: today.mondayOfWeek().minusWeeks(7 - index), grams: index * 15, alcoholFreeDays: 3 }));
    const withBands = alcoholChartModel(weeks, today, true);
    expect(withBands.bands.map((band) => [band.band, band.from, band.label])).toEqual([
      ["LOW_RISK", 0, "risikoarm"],
      ["MODERATE_RISK", 27, "moderates Risiko"],
      ["HIGH_RISK", 81, "riskant"],
    ]);
    expect(withBands.bands.at(-1)?.to).toBe(withBands.max);
    expect(withBands.max).toBeGreaterThan(81);
    const withoutBands = alcoholChartModel(weeks, today, false);
    expect(withoutBands.bands).toEqual([]);
    for (const model of [withBands, withoutBands]) {
      expect(model.notes[0]).toBe("Es gibt keine unbedenkliche Menge Alkohol. Weniger ist besser.");
    }
    expect(withBands.bars.at(-1)).toMatchObject({ isCurrent: true, shortLabel: "jetzt", detail: "3 alkoholfreie Tage · riskant" });
    expect(withBands.bars[0].detail).toBe("3 alkoholfreie Tage");
  });
});

describe("Anzeige-Formate", () => {
  it("Gramm und Prozent", () => {
    expect(formatGrams(1.6)).toBe("1,6 g");
    expect(formatGrams(34.98)).toBe("35 g");
    expect(formatGrams(0)).toBe("0 g");
    expect(formatPercentChange(-20.4)).toBe("−20 %");
    expect(formatPercentChange(5.2)).toBe("+5 %");
    expect(formatPercentChange(0.3)).toBe("±0 %");
  });

  it("Tage, relative Zeiten und Anfangsbuchstaben", () => {
    const zone = ianaTimeZone("Europe/Berlin");
    const now = Date.parse("2026-11-10T17:00:00Z");
    expect(dayHeading(today, today)).toBe("Heute");
    expect(dayHeading(today.minusDays(1), today)).toBe("Gestern");
    expect(dayHeading(LocalDate.of(2026, 11, 2), today)).toBe("Montag, 2. November");
    expect(dayHeading(LocalDate.of(2025, 12, 24), today)).toBe("Mittwoch, 24. Dezember 2025");
    expect(formatRelative(now - 20_000, now, zone)).toBe("gerade eben");
    expect(formatRelative(now - 5 * 60_000, now, zone)).toBe("vor 5 Min.");
    expect(formatRelative(now - 3 * 3_600_000, now, zone)).toBe("vor 3 Std.");
    expect(initials("Anna Lena Muster")).toBe("AM");
    expect(initials("ben")).toBe("B");
    expect(initials("  ")).toBe("?");
  });
});
