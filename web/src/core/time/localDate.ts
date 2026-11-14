/**
 * Kalendertag ohne Uhrzeit und ohne Zeitzone (wie `java.time.LocalDate` im Kotlin-Kern).
 *
 * Intern als Tag seit 1970-01-01 gespeichert, damit Rechnen (± Tage, Wochenbeginn) exakt und
 * unabhängig von Sommer-/Winterzeit ist.
 */
export class LocalDate {
  private constructor(readonly epochDay: number) {}

  static ofEpochDay(epochDay: number): LocalDate {
    if (!Number.isInteger(epochDay)) throw new RangeError("epochDay muss ganzzahlig sein.");
    return new LocalDate(epochDay);
  }

  /** @param month 1–12 */
  static of(year: number, month: number, day: number): LocalDate {
    const ms = Date.UTC(year, month - 1, day);
    const date = new Date(ms);
    if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 || date.getUTCDate() !== day) {
      throw new RangeError(`Ungültiges Datum: ${year}-${month}-${day}`);
    }
    return new LocalDate(Math.round(ms / MS_PER_DAY));
  }

  /** „2026-11-10“ */
  static parse(iso: string): LocalDate {
    const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso);
    if (!match) throw new RangeError(`Ungültiges Datum: ${iso}`);
    return LocalDate.of(Number(match[1]), Number(match[2]), Number(match[3]));
  }

  get year(): number {
    return this.utc().getUTCFullYear();
  }

  /** 1–12 */
  get month(): number {
    return this.utc().getUTCMonth() + 1;
  }

  get dayOfMonth(): number {
    return this.utc().getUTCDate();
  }

  /** ISO-Wochentag: 1 = Montag … 7 = Sonntag. */
  get dayOfWeek(): number {
    return mod(this.epochDay + 3, 7) + 1;
  }

  plusDays(days: number): LocalDate {
    return new LocalDate(this.epochDay + days);
  }

  minusDays(days: number): LocalDate {
    return new LocalDate(this.epochDay - days);
  }

  minusWeeks(weeks: number): LocalDate {
    return new LocalDate(this.epochDay - 7 * weeks);
  }

  /** Montag derselben Woche (oder der Tag selbst, wenn er ein Montag ist). */
  mondayOfWeek(): LocalDate {
    return new LocalDate(this.epochDay - (this.dayOfWeek - 1));
  }

  isBefore(other: LocalDate): boolean {
    return this.epochDay < other.epochDay;
  }

  isAfter(other: LocalDate): boolean {
    return this.epochDay > other.epochDay;
  }

  equals(other: LocalDate | null | undefined): boolean {
    return other != null && other.epochDay === this.epochDay;
  }

  toString(): string {
    const y = String(this.year).padStart(4, "0");
    const m = String(this.month).padStart(2, "0");
    const d = String(this.dayOfMonth).padStart(2, "0");
    return `${y}-${m}-${d}`;
  }

  toJSON(): string {
    return this.toString();
  }

  private utc(): Date {
    return new Date(this.epochDay * MS_PER_DAY);
  }
}

export const MS_PER_DAY = 86_400_000;

function mod(value: number, divisor: number): number {
  return ((value % divisor) + divisor) % divisor;
}

/** Ordnet einem Zeitpunkt (Millisekunden seit 1970, UTC) den Kalendertag in einer Zeitzone zu. */
export interface TimeZone {
  readonly id: string;
  localDate(epochMillis: number): LocalDate;
}

/** Die Zeitzone des Geräts (schnell, über die lokalen `Date`-Getter). */
export const systemTimeZone: TimeZone = {
  get id(): string {
    return Intl.DateTimeFormat().resolvedOptions().timeZone;
  },
  localDate(epochMillis: number): LocalDate {
    const date = new Date(epochMillis);
    return LocalDate.of(date.getFullYear(), date.getMonth() + 1, date.getDate());
  },
};

/** Eine IANA-Zeitzone (z. B. „Europe/Berlin“), unabhängig von der Gerätezeitzone – vor allem für Tests. */
export function ianaTimeZone(id: string): TimeZone {
  const format = new Intl.DateTimeFormat("en-US", {
    timeZone: id,
    year: "numeric",
    month: "numeric",
    day: "numeric",
  });
  return {
    id,
    localDate(epochMillis: number): LocalDate {
      let year = 0;
      let month = 0;
      let day = 0;
      for (const part of format.formatToParts(epochMillis)) {
        if (part.type === "year") year = Number(part.value);
        else if (part.type === "month") month = Number(part.value);
        else if (part.type === "day") day = Number(part.value);
      }
      return LocalDate.of(year, month, day);
    },
  };
}
