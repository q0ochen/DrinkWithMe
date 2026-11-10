package com.drinkwithme.core.bar

/**
 * Speichert ein neues oder geändertes Bar-Getränk: bereinigt es ([normalized]), prüft es und legt es ab.
 *
 * @throws InvalidBarItemException wenn das Getränk ungültig ist; dann wird nichts gespeichert.
 */
class SaveBarItemUseCase(
    private val writer: BarWriter,
    private val validator: BarItemValidator = DefaultBarItemValidator(),
) {
    operator fun invoke(item: BarItem): BarItem {
        val clean = validator.requireValid(item.normalized())
        writer.save(clean)
        return clean
    }
}

/**
 * Erster Start: übernimmt die ausgewählten Vorschläge in die eigene Bar, in der Reihenfolge der Auswahl.
 * Unbekannte IDs werden ignoriert. Gibt die gespeicherten Getränke zurück.
 */
class SetUpBarFromCatalogUseCase(
    private val writer: BarWriter,
    private val lookup: (String) -> BarItem? = DefaultBarCatalog::item,
) {
    operator fun invoke(selectedIds: List<String> = DefaultBarCatalog.defaultSelectionIds): List<BarItem> {
        val items = selectedIds.distinct()
            .mapNotNull(lookup)
            .mapIndexed { index, item -> item.copy(sortOrder = index) }
        items.forEach(writer::save)
        return items
    }
}
