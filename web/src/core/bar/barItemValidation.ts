import { BAR_ITEM_LIMITS, type BarItem } from "./barItem";

/** Ein Verstoß gegen die Regeln für Bar-Getränke (Meldungen wie im Kotlin-Kern, werden direkt angezeigt). */
export type BarItemError =
  | { readonly kind: "EmptyName"; readonly message: string }
  | { readonly kind: "NameTooLong"; readonly maximum: number; readonly message: string }
  | { readonly kind: "ServingOutOfRange"; readonly min: number; readonly max: number; readonly message: string }
  | { readonly kind: "AlcoholOutOfRange"; readonly maximum: number; readonly message: string }
  | { readonly kind: "SugarOutOfRange"; readonly maximum: number; readonly message: string }
  | { readonly kind: "BlankAlias"; readonly message: string }
  | { readonly kind: "AliasTooLong"; readonly maximum: number; readonly message: string };

/** „80“ statt „80.0“, „12,5“ statt „12.5“. */
function germanNumber(value: number): string {
  return Number.isInteger(value) ? String(Math.trunc(value)) : String(value).replace(".", ",");
}

export const BarItemErrors = {
  emptyName: (): BarItemError => ({ kind: "EmptyName", message: "Bitte gib einen Namen ein." }),
  nameTooLong: (maximum: number): BarItemError => ({
    kind: "NameTooLong",
    maximum,
    message: `Der Name darf höchstens ${maximum} Zeichen lang sein.`,
  }),
  servingOutOfRange: (min: number, max: number): BarItemError => ({
    kind: "ServingOutOfRange",
    min,
    max,
    message: `Die Portion muss zwischen ${min} und ${max} ml liegen.`,
  }),
  alcoholOutOfRange: (maximum: number): BarItemError => ({
    kind: "AlcoholOutOfRange",
    maximum,
    message: `Der Alkoholgehalt muss zwischen 0 und ${germanNumber(maximum)} Vol.-% liegen.`,
  }),
  sugarOutOfRange: (maximum: number): BarItemError => ({
    kind: "SugarOutOfRange",
    maximum,
    message: `Der Zuckergehalt muss zwischen 0 und ${germanNumber(maximum)} g pro 100 ml liegen.`,
  }),
  blankAlias: (): BarItemError => ({ kind: "BlankAlias", message: "Ein Alternativname ist leer." }),
  aliasTooLong: (maximum: number): BarItemError => ({
    kind: "AliasTooLong",
    maximum,
    message: `Ein Alternativname darf höchstens ${maximum} Zeichen lang sein.`,
  }),
} as const;

/** Ungültiges Bar-Getränk; `errors` enthält alle gefundenen Verstöße. */
export class InvalidBarItemError extends Error {
  constructor(readonly errors: readonly BarItemError[]) {
    super(errors.map((error) => error.message).join(" "));
    this.name = "InvalidBarItemError";
  }
}

/** Prüft ein Bar-Getränk. Eigene Regeln werden als weitere Implementierung eingesteckt (Open/Closed). */
export interface BarItemValidator {
  /** Alle Verstöße; eine leere Liste bedeutet „gültig“. */
  validate(item: BarItem): BarItemError[];
}

/** Wirft {@link InvalidBarItemError}, wenn `item` ungültig ist, sonst gibt es `item` unverändert zurück. */
export function requireValid(validator: BarItemValidator, item: BarItem): BarItem {
  const errors = validator.validate(item);
  if (errors.length > 0) throw new InvalidBarItemError(errors);
  return item;
}

export interface BarItemRules {
  readonly maximumNameLength: number;
  readonly servingMin: number;
  readonly servingMax: number;
  readonly maximumAlcoholPercent: number;
  readonly maximumSugarPer100Ml: number;
  readonly maximumAliasLength: number;
}

const DEFAULT_RULES: BarItemRules = {
  maximumNameLength: BAR_ITEM_LIMITS.NAME_MAX_LENGTH,
  servingMin: BAR_ITEM_LIMITS.SERVING_MIN_MILLILITERS,
  servingMax: BAR_ITEM_LIMITS.SERVING_MAX_MILLILITERS,
  maximumAlcoholPercent: BAR_ITEM_LIMITS.ALCOHOL_PERCENT_MAX,
  maximumSugarPer100Ml: BAR_ITEM_LIMITS.SUGAR_GRAMS_PER_100_ML_MAX,
  maximumAliasLength: BAR_ITEM_LIMITS.ALIAS_MAX_LENGTH,
};

/** Anzahl Unicode-Zeichen (wie `codePointCount` in Kotlin bzw. `char_length` auf dem Server). */
export function characterCount(text: string): number {
  return [...text].length;
}

/**
 * Regeln aus dem Vertrag: Name 1–40 Zeichen (ohne Rand-Leerraum), Portion 1–2000 ml, 0–80 Vol.-%,
 * 0–100 g Zucker pro 100 ml, Alternativnamen nicht leer.
 */
export class DefaultBarItemValidator implements BarItemValidator {
  private readonly rules: BarItemRules;

  constructor(rules: Partial<BarItemRules> = {}) {
    this.rules = { ...DEFAULT_RULES, ...rules };
  }

  validate(item: BarItem): BarItemError[] {
    const { rules } = this;
    const errors: BarItemError[] = [];
    const name = item.name.trim();
    if (name.length === 0) errors.push(BarItemErrors.emptyName());
    else if (characterCount(name) > rules.maximumNameLength) errors.push(BarItemErrors.nameTooLong(rules.maximumNameLength));
    const serving = item.servingMilliliters;
    if (!Number.isInteger(serving) || serving < rules.servingMin || serving > rules.servingMax) {
      errors.push(BarItemErrors.servingOutOfRange(rules.servingMin, rules.servingMax));
    }
    if (!isWithin(item.alcoholPercent, rules.maximumAlcoholPercent)) {
      errors.push(BarItemErrors.alcoholOutOfRange(rules.maximumAlcoholPercent));
    }
    if (!isWithin(item.sugarGramsPer100Ml, rules.maximumSugarPer100Ml)) {
      errors.push(BarItemErrors.sugarOutOfRange(rules.maximumSugarPer100Ml));
    }
    const aliases = item.aliases.map((alias) => alias.trim());
    if (aliases.some((alias) => alias.length === 0)) errors.push(BarItemErrors.blankAlias());
    if (aliases.some((alias) => characterCount(alias) > rules.maximumAliasLength)) {
      errors.push(BarItemErrors.aliasTooLong(rules.maximumAliasLength));
    }
    return errors;
  }
}

function isWithin(value: number, maximum: number): boolean {
  return Number.isFinite(value) && value >= 0 && value <= maximum;
}
