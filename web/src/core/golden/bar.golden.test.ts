// Golden-Tests: dieselben Eingaben und Erwartungen wie in den Kotlin-Tests
// DefaultBarCatalogTest, DrinkIconAndCategoryTest, BarItemValidatorTest und BarRepositoryAndUseCaseTest.
import { describe, expect, it } from "vitest";
import { normalizedBarItem } from "../bar/barItem";
import { BarItemErrors, DefaultBarItemValidator, InvalidBarItemError, requireValid } from "../bar/barItemValidation";
import { InMemoryBarRepository } from "../bar/barRepository";
import { SaveBarItemUseCase, SetUpBarFromCatalogUseCase } from "../bar/barUseCases";
import {
  CATALOG_ENTRIES,
  CATALOG_ID_PREFIX,
  CATALOG_ITEMS,
  catalogEntry,
  CatalogIds,
  catalogItem,
  DEFAULT_SELECTION_IDS,
  defaultSelection,
} from "../bar/defaultBarCatalog";
import { categoryDisplayName, DRINK_CATEGORIES, drinkCategoryFromName } from "../bar/drinkCategory";
import { defaultIconFor, DRINK_ICON_KEY_PATTERN, DRINK_ICON_KEYS, drinkIconFromKey, drinkIconFromKeyOrNull } from "../bar/drinkIcon";
import { barItem } from "../testing/fixtures";

describe("Kotlin-Kern: DefaultBarCatalogTest", () => {
  // Name · Zucker g/100 ml · Vol.-% · Portion ml · Icon · Kategorie – exakt laut Vertrag.
  const expected = [
    ["Wasser", 0.0, 0.0, 250, "water", "WATER"],
    ["Mineralwasser", 0.0, 0.0, 250, "sparkling_water", "WATER"],
    ["Kaffee", 0.0, 0.0, 200, "coffee", "COFFEE"],
    ["Tee", 0.0, 0.0, 250, "tea", "TEA"],
    ["Cola", 10.6, 0.0, 330, "cola", "SOFT_DRINK"],
    ["Cola zero", 0.0, 0.0, 330, "cola", "SOFT_DRINK"],
    ["Limonade", 8.0, 0.0, 330, "lemonade", "SOFT_DRINK"],
    ["Eistee", 7.5, 0.0, 500, "iced_tea", "SOFT_DRINK"],
    ["Energydrink", 11.0, 0.0, 250, "energy_drink", "ENERGY_DRINK"],
    ["Orangensaft", 9.0, 0.0, 200, "juice", "JUICE"],
    ["Apfelsaft", 10.0, 0.0, 200, "juice", "JUICE"],
    ["Apfelschorle", 5.5, 0.0, 500, "apple_spritzer", "JUICE"],
    ["Malzbier", 9.0, 0.0, 330, "malt_beer", "SOFT_DRINK"],
    ["Milch", 4.8, 0.0, 200, "milk", "MILK"],
    ["Pils", 0.3, 4.9, 500, "beer", "BEER"],
    ["Weizenbier", 0.3, 5.4, 500, "wheat_beer", "BEER"],
    ["Alkoholfreies Bier", 2.5, 0.4, 500, "beer", "BEER"],
    ["Radler", 5.0, 2.5, 500, "radler", "BEER"],
    ["Rotwein trocken", 0.3, 12.0, 125, "red_wine", "WINE"],
    ["Weißwein trocken", 0.3, 11.5, 125, "white_wine", "WINE"],
    ["Sekt trocken", 2.5, 11.0, 100, "sparkling_wine", "SPARKLING_WINE"],
    ["Schnaps", 0.0, 40.0, 20, "shot", "SPIRIT"],
    ["Longdrink", 8.7, 5.3, 240, "longdrink", "SPIRIT"],
    ["Cocktail", 8.0, 10.0, 200, "cocktail", "COCKTAIL"],
  ];

  it("containsExactlyTheContractValues", () => {
    const actual = CATALOG_ITEMS.map((it) => [
      it.name,
      it.sugarGramsPer100Ml,
      it.alcoholPercent,
      it.servingMilliliters,
      it.icon,
      it.category,
    ]);
    expect(actual).toEqual(expected);
  });

  it("onlyMilkAndCocktailAreMarkedAsEstimates", () => {
    expect(CATALOG_ENTRIES.filter((it) => it.isEstimate).map((it) => it.item.name)).toEqual(["Milch", "Cocktail"]);
    expect(catalogEntry(CatalogIds.MILCH)?.note).toBe("Zucker = Milchzucker (Schätzwert)");
    expect(catalogEntry(CatalogIds.COCKTAIL)?.note).toBe("Schätzwert");
    expect(catalogEntry(CatalogIds.LONGDRINK)?.note).toBe("Spirituose + Cola");
  });

  it("idsAreStableUniqueAndPrefixed", () => {
    const ids = CATALOG_ITEMS.map((it) => it.id);
    expect(new Set(ids).size).toBe(ids.length);
    expect(ids.every((id) => id.startsWith(CATALOG_ID_PREFIX))).toBe(true);
    expect(CatalogIds.APFELSCHORLE).toBe("katalog-apfelschorle");
  });

  it("everyItemIsValidAndMarkedAsDefault", () => {
    const validator = new DefaultBarItemValidator();
    for (const item of CATALOG_ITEMS) {
      expect(validator.validate(item), item.name).toEqual([]);
      expect(item.valueSource).toBe("DEFAULT");
    }
  });

  it("sortOrderFollowsTheList", () => {
    expect(CATALOG_ITEMS.map((it) => it.sortOrder)).toEqual(CATALOG_ITEMS.map((_, index) => index));
  });

  it("defaultSelectionIsWaterCoffeeTeaAppleSpritzerColaPilsRedWine", () => {
    const selection = defaultSelection();
    expect(selection.map((it) => it.name)).toEqual(["Wasser", "Kaffee", "Tee", "Apfelschorle", "Cola", "Pils", "Rotwein trocken"]);
    expect(selection.map((it) => it.sortOrder)).toEqual([0, 1, 2, 3, 4, 5, 6]);
    expect(selection.map((it) => it.id)).toEqual(DEFAULT_SELECTION_IDS);
  });

  it("aliasesHelpVoiceInput", () => {
    expect(catalogItem(CatalogIds.APFELSCHORLE)?.aliases).toContain("Schorle");
    expect(catalogItem(CatalogIds.APFELSCHORLE)?.aliases).toContain("Apfelsaftschorle");
    expect(catalogItem(CatalogIds.PILS)?.aliases).toContain("Bier");
  });

  it("unknownIdsReturnNull", () => {
    expect(catalogItem("katalog-unbekannt")).toBeNull();
    expect(catalogEntry("wasser")).toBeNull();
  });
});

describe("Kotlin-Kern: DrinkIconAndCategoryTest", () => {
  it("iconKeysMatchTheContract", () => {
    expect([...DRINK_ICON_KEYS]).toEqual([
      "water", "sparkling_water", "coffee", "tea", "juice", "apple_spritzer", "cola", "lemonade", "iced_tea",
      "energy_drink", "milk", "malt_beer", "beer", "wheat_beer", "radler", "red_wine", "white_wine",
      "sparkling_wine", "shot", "longdrink", "cocktail", "other",
    ]);
  });

  it("iconKeysAreUniqueAndAcceptedByTheServer", () => {
    expect(new Set(DRINK_ICON_KEYS).size).toBe(DRINK_ICON_KEYS.length);
    for (const key of DRINK_ICON_KEYS) expect(DRINK_ICON_KEY_PATTERN.test(key), key).toBe(true);
  });

  it("iconLookupByKey", () => {
    expect(drinkIconFromKey("apple_spritzer")).toBe("apple_spritzer");
    expect(drinkIconFromKey("unicorn_milk")).toBe("other");
    expect(drinkIconFromKeyOrNull("unicorn_milk")).toBeNull();
    for (const key of DRINK_ICON_KEYS) expect(drinkIconFromKeyOrNull(key)).toBe(key);
  });

  it("defaultIconPerCategory", () => {
    expect(defaultIconFor("WINE")).toBe("red_wine");
    expect(defaultIconFor("SPIRIT")).toBe("shot");
    expect(defaultIconFor("SOFT_DRINK")).toBe("lemonade");
    expect(defaultIconFor("OTHER")).toBe("other");
  });

  it("categoriesMatchTheContract", () => {
    expect([...DRINK_CATEGORIES]).toEqual([
      "WATER", "COFFEE", "TEA", "JUICE", "SOFT_DRINK", "ENERGY_DRINK", "MILK", "BEER", "WINE",
      "SPARKLING_WINE", "SPIRIT", "COCKTAIL", "OTHER",
    ]);
  });

  it("categoryFromNameFallsBackToOther", () => {
    expect(drinkCategoryFromName("SPARKLING_WINE")).toBe("SPARKLING_WINE");
    expect(drinkCategoryFromName("KOMBUCHA")).toBe("OTHER");
  });

  it("categoriesHaveGermanNamesWithoutEmojis", () => {
    expect(categoryDisplayName("WATER")).toBe("Wasser");
    expect(categoryDisplayName("SPARKLING_WINE")).toBe("Sekt");
    expect(categoryDisplayName("SPIRIT")).toBe("Spirituose");
    for (const category of DRINK_CATEGORIES) expect(categoryDisplayName(category)).toMatch(/^[\p{L} ]+$/u);
  });
});

describe("Kotlin-Kern: BarItemValidatorTest", () => {
  const validator = new DefaultBarItemValidator();
  const valid = barItem("Apfelschorle", { aliases: ["Schorle"], sugarPer100Ml: 5.5, serving: 500 });

  it("acceptsValidItem", () => expect(validator.validate(valid)).toEqual([]));

  it("nameMustNotBeBlank", () => {
    expect(validator.validate({ ...valid, name: "   " })).toEqual([BarItemErrors.emptyName()]);
  });

  it("nameMayHaveFortyCharacters", () => {
    expect(validator.validate({ ...valid, name: "a".repeat(40) })).toEqual([]);
    expect(validator.validate({ ...valid, name: `  ${"a".repeat(40)}  ` })).toEqual([]);
    expect(validator.validate({ ...valid, name: "a".repeat(41) })).toEqual([BarItemErrors.nameTooLong(40)]);
  });

  it("nameLengthCountsCharactersLikeTheServer", () => {
    expect(validator.validate({ ...valid, name: "🍺".repeat(40) })).toEqual([]);
  });

  it("servingMustBeBetween1And2000", () => {
    for (const serving of [1, 2000]) expect(validator.validate({ ...valid, servingMilliliters: serving })).toEqual([]);
    for (const serving of [0, -1, 2001]) {
      expect(validator.validate({ ...valid, servingMilliliters: serving })).toEqual([BarItemErrors.servingOutOfRange(1, 2000)]);
    }
  });

  it("alcoholMustBeBetween0And80Percent", () => {
    for (const value of [0.0, 0.4, 80.0]) expect(validator.validate({ ...valid, alcoholPercent: value })).toEqual([]);
    for (const value of [-0.1, 80.1, Number.NaN, Number.POSITIVE_INFINITY]) {
      expect(validator.validate({ ...valid, alcoholPercent: value })).toEqual([BarItemErrors.alcoholOutOfRange(80)]);
    }
  });

  it("sugarMustBeBetween0And100Grams", () => {
    for (const value of [0.0, 100.0]) expect(validator.validate({ ...valid, sugarGramsPer100Ml: value })).toEqual([]);
    for (const value of [-1.0, 100.5, Number.NaN]) {
      expect(validator.validate({ ...valid, sugarGramsPer100Ml: value })).toEqual([BarItemErrors.sugarOutOfRange(100)]);
    }
  });

  it("aliasesMustNotBeBlankOrTooLong", () => {
    expect(validator.validate({ ...valid, aliases: [" "] })).toEqual([BarItemErrors.blankAlias()]);
    expect(validator.validate({ ...valid, aliases: ["x".repeat(41)] })).toEqual([BarItemErrors.aliasTooLong(40)]);
  });

  it("collectsAllErrorsWithGermanMessages", () => {
    const errors = validator.validate({ ...valid, name: "", servingMilliliters: 0, alcoholPercent: 99.0, sugarGramsPer100Ml: 101.0 });
    expect(errors.map((it) => it.message)).toEqual([
      "Bitte gib einen Namen ein.",
      "Die Portion muss zwischen 1 und 2000 ml liegen.",
      "Der Alkoholgehalt muss zwischen 0 und 80 Vol.-% liegen.",
      "Der Zuckergehalt muss zwischen 0 und 100 g pro 100 ml liegen.",
    ]);
    expect(BarItemErrors.nameTooLong(40).message).toBe("Der Name darf höchstens 40 Zeichen lang sein.");
    expect(BarItemErrors.aliasTooLong(40).message).toBe("Ein Alternativname darf höchstens 40 Zeichen lang sein.");
    expect(BarItemErrors.alcoholOutOfRange(12.5).message).toBe("Der Alkoholgehalt muss zwischen 0 und 12,5 Vol.-% liegen.");
  });

  it("requireValidThrowsWithAllErrors", () => {
    expect(requireValid(validator, valid)).toBe(valid);
    let error: unknown;
    try {
      requireValid(validator, { ...valid, name: "", servingMilliliters: 0 });
    } catch (thrown) {
      error = thrown;
    }
    expect(error).toBeInstanceOf(InvalidBarItemError);
    expect((error as InvalidBarItemError).errors).toHaveLength(2);
    expect((error as InvalidBarItemError).message).toBe("Bitte gib einen Namen ein. Die Portion muss zwischen 1 und 2000 ml liegen.");
  });

  it("customRulesCanBePluggedIn", () => {
    expect(new DefaultBarItemValidator({ maximumNameLength: 5 }).validate(valid)).toEqual([BarItemErrors.nameTooLong(5)]);
  });

  it("normalizedTrimsAndRemovesDuplicateAliases", () => {
    const messy = { ...valid, name: "  Apfelschorle ", aliases: [" Schorle ", "", "schorle", "APFELSCHORLE", "Saftschorle"] };
    expect(normalizedBarItem(messy)).toEqual({ ...valid, aliases: ["Schorle", "Saftschorle"] });
  });
});

describe("Kotlin-Kern: BarRepositoryAndUseCaseTest", () => {
  const water = barItem("Wasser", { sortOrder: 0 });
  const tea = barItem("Tee", { sortOrder: 1 });
  const cola = barItem("Cola", { sortOrder: 2 });

  it("itemsAreSortedBySortOrder", async () => {
    expect(await new InMemoryBarRepository([cola, water, tea]).items()).toEqual([water, tea, cola]);
  });

  it("saveAddsOrReplaces", async () => {
    const repository = new InMemoryBarRepository([water]);
    await repository.save(tea);
    await repository.save({ ...water, servingMilliliters: 500 });
    expect(await repository.items()).toEqual([{ ...water, servingMilliliters: 500 }, tea]);
    expect(await repository.item(tea.id)).toEqual(tea);
  });

  it("deleteRemovesAndIgnoresUnknownIds", async () => {
    const repository = new InMemoryBarRepository([water, tea]);
    await repository.delete(water.id);
    await repository.delete("unbekannt");
    expect(await repository.items()).toEqual([tea]);
    expect(await repository.item(water.id)).toBeNull();
  });

  it("reorderPutsNamedItemsFirstAndKeepsTheRest", async () => {
    const repository = new InMemoryBarRepository([water, tea, cola]);
    await repository.reorder([cola.id, "unbekannt", cola.id]);
    expect((await repository.items()).map((it) => it.id)).toEqual([cola.id, water.id, tea.id]);
    expect((await repository.items()).map((it) => it.sortOrder)).toEqual([0, 1, 2]);
    await repository.reorder([tea.id, water.id, cola.id]);
    expect((await repository.items()).map((it) => it.id)).toEqual([tea.id, water.id, cola.id]);
  });

  it("saveBarItemNormalizesValidatesAndSaves", async () => {
    const repository = new InMemoryBarRepository();
    const saved = await new SaveBarItemUseCase(repository).execute({ ...water, name: " Wasser ", aliases: [" Leitungswasser ", ""] });
    expect(saved).toEqual({ ...water, aliases: ["Leitungswasser"] });
    expect(await repository.items()).toEqual([saved]);
  });

  it("saveBarItemRejectsInvalidItemsWithoutSaving", async () => {
    const repository = new InMemoryBarRepository();
    await expect(new SaveBarItemUseCase(repository).execute({ ...water, servingMilliliters: 0 })).rejects.toBeInstanceOf(
      InvalidBarItemError,
    );
    expect(await repository.items()).toEqual([]);
  });

  it("setUpBarSavesTheDefaultSelectionInOrder", async () => {
    const repository = new InMemoryBarRepository();
    const saved = await new SetUpBarFromCatalogUseCase(repository).execute();
    expect(saved).toEqual(defaultSelection());
    expect(await repository.items()).toEqual(saved);
  });

  it("setUpBarUsesTheChosenOrderAndIgnoresUnknownIds", async () => {
    const repository = new InMemoryBarRepository();
    const saved = await new SetUpBarFromCatalogUseCase(repository).execute([
      CatalogIds.SEKT,
      "katalog-unbekannt",
      CatalogIds.WASSER,
      CatalogIds.SEKT,
    ]);
    expect(saved.map((it) => it.name)).toEqual(["Sekt trocken", "Wasser"]);
    expect((await repository.items()).map((it) => it.sortOrder)).toEqual([0, 1]);
  });
});
