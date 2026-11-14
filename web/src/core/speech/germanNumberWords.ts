/**
 * Deutsche Zahlwörter zum Vorlesen (Port von `GermanNumberWords`): „zweihundertfünfzig“, „siebenundzwanzig“,
 * „zwei Komma fünf“. Ganze Zahlen bis 999 999 999 werden ausgeschrieben, größere bleiben Ziffern.
 * Nachkommastellen werden einzeln gelesen („zwölf Komma zwei fünf“).
 */
const UNITS = [
  "null", "eins", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun",
  "zehn", "elf", "zwölf", "dreizehn", "vierzehn", "fünfzehn", "sechzehn", "siebzehn", "achtzehn", "neunzehn",
];
const TENS = ["", "", "zwanzig", "dreißig", "vierzig", "fünfzig", "sechzig", "siebzig", "achtzig", "neunzig"];
const MILLION = 1_000_000;
const LARGEST_WRITTEN = 999_999_999;

/** Grundzahl als Wort; „eins“ für 1. */
export function cardinal(number: number): string {
  if (!Number.isInteger(number)) throw new RangeError("cardinal erwartet eine ganze Zahl.");
  if (number < 0) return `minus ${cardinal(-number)}`;
  if (number === 0) return UNITS[0];
  if (number > LARGEST_WRITTEN) return String(number);
  return belowBillion(number);
}

/** Eine kaufmännisch gerundete Dezimalzahl (wie `BigDecimal.valueOf(x).setScale(n, HALF_UP).stripTrailingZeros()`). */
export interface RoundedDecimal {
  readonly negative: boolean;
  readonly integer: bigint;
  /** Nachkommastellen ohne abschließende Nullen, z. B. „25“ oder „05“; leer bei ganzen Zahlen. */
  readonly fraction: string;
}

/**
 * Rundet `value` kaufmännisch (HALF_UP auf der kürzesten Dezimaldarstellung, wie Javas `BigDecimal.valueOf`)
 * auf höchstens `maxFractionDigits` Nachkommastellen.
 */
export function roundHalfUp(value: number, maxFractionDigits: number): RoundedDecimal {
  if (!Number.isFinite(value)) throw new RangeError("Nur endliche Zahlen können vorgelesen werden.");
  if (!Number.isInteger(maxFractionDigits) || maxFractionDigits < 0) {
    throw new RangeError("maxFractionDigits darf nicht negativ sein.");
  }
  const { negative, digits, exponent } = decompose(value);
  const shift = exponent + maxFractionDigits;
  let scaled: bigint;
  if (shift >= 0) {
    scaled = BigInt(digits) * 10n ** BigInt(shift);
  } else {
    const divisor = 10n ** BigInt(-shift);
    const whole = BigInt(digits);
    scaled = whole / divisor;
    if ((whole % divisor) * 2n >= divisor) scaled += 1n;
  }
  if (scaled === 0n) return { negative: false, integer: 0n, fraction: "" };
  let scale = maxFractionDigits;
  while (scale > 0 && scaled % 10n === 0n) {
    scaled /= 10n;
    scale--;
  }
  const factor = 10n ** BigInt(scale);
  const integer = scaled / factor;
  const fraction = scale === 0 ? "" : (scaled % factor).toString().padStart(scale, "0");
  return { negative, integer, fraction };
}

/** Kürzeste Dezimaldarstellung: Wert = (−1)^negative × digits × 10^exponent. */
function decompose(value: number): { negative: boolean; digits: string; exponent: number } {
  const text = String(Math.abs(value));
  const negative = value < 0 || Object.is(value, -0);
  const [mantissa, exponentPart] = text.split("e");
  const exponentFromE = exponentPart === undefined ? 0 : Number(exponentPart);
  const [intPart, fracPart = ""] = mantissa.split(".");
  const digits = (intPart + fracPart).replace(/^0+(?=\d)/, "");
  return { negative, digits, exponent: exponentFromE - fracPart.length };
}

/** `true`, wenn der gerundete Wert genau 1 ist (für „ein Gramm“). */
export function roundsToOne(value: number, maxFractionDigits: number): boolean {
  const rounded = roundHalfUp(value, maxFractionDigits);
  return !rounded.negative && rounded.integer === 1n && rounded.fraction === "";
}

/** −1, 0 oder 1 für den gerundeten Wert. */
export function roundedSignum(value: number, maxFractionDigits: number): number {
  const rounded = roundHalfUp(value, maxFractionDigits);
  if (rounded.integer === 0n && rounded.fraction === "") return 0;
  return rounded.negative ? -1 : 1;
}

/**
 * Dezimalzahl, kaufmännisch auf `maxFractionDigits` Stellen gerundet, ohne überflüssige Nullen:
 * 2,5 → „zwei Komma fünf“, 2,0 → „zwei“, 0,4 → „null Komma vier“.
 */
export function decimal(value: number, maxFractionDigits = 1): string {
  const rounded = roundHalfUp(value, maxFractionDigits);
  const integerPart = rounded.integer < 2n ** 62n ? cardinal(Number(rounded.integer)) : rounded.integer.toString();
  const words =
    rounded.fraction.length === 0
      ? integerPart
      : `${integerPart} Komma ${[...rounded.fraction].map((digit) => UNITS[Number(digit)]).join(" ")}`;
  const isZero = rounded.integer === 0n && rounded.fraction.length === 0;
  return rounded.negative && !isZero ? `minus ${words}` : words;
}

/**
 * Ordnungszahl von 1 bis 99 mit der Endung „-en“ (nach „dem“/„am“): 1 → „ersten“, 3 → „dritten“,
 * 7 → „siebten“, 20 → „zwanzigsten“, 31 → „einunddreißigsten“.
 */
export function ordinalDative(number: number): string {
  if (!Number.isInteger(number) || number < 1 || number > 99) {
    throw new RangeError("Ordnungszahlen werden von 1 bis 99 unterstützt.");
  }
  if (number === 1) return "ersten";
  if (number === 3) return "dritten";
  if (number === 7) return "siebten";
  if (number === 8) return "achten";
  if (number < 20) return `${cardinal(number)}ten`;
  return `${cardinal(number)}sten`;
}

function belowBillion(number: number): string {
  const millions = Math.trunc(number / MILLION);
  const rest = number % MILLION;
  if (millions === 0) return belowMillion(rest);
  const millionWords = millions === 1 ? "eine Million" : `${belowThousand(millions, false)} Millionen`;
  return rest === 0 ? millionWords : `${millionWords} ${belowMillion(rest)}`;
}

function belowMillion(number: number): string {
  const thousands = Math.trunc(number / 1000);
  const rest = number % 1000;
  const thousandWords = thousands === 0 ? "" : `${belowThousand(thousands, false)}tausend`;
  const restWords = rest === 0 ? "" : belowThousand(rest, true);
  return thousandWords + restWords;
}

/** `standalone`: `true`, wenn die Zahl am Ende steht („…eins“), sonst „ein…“ („eintausend“). */
function belowThousand(number: number, standalone: boolean): string {
  const hundreds = Math.trunc(number / 100);
  const rest = number % 100;
  const hundredWords = hundreds === 0 ? "" : `${prefixForm(hundreds)}hundert`;
  const restWords = rest === 0 ? "" : belowHundred(rest, standalone);
  return hundredWords + restWords;
}

function belowHundred(number: number, standalone: boolean): string {
  if (number === 1) return standalone ? "eins" : "ein";
  if (number < 20) return UNITS[number];
  if (number % 10 === 0) return TENS[number / 10];
  return `${prefixForm(number % 10)}und${TENS[Math.trunc(number / 10)]}`;
}

function prefixForm(digit: number): string {
  return digit === 1 ? "ein" : UNITS[digit];
}
