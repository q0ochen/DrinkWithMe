import Foundation
import DrinkWithMeCore

/// Composition Root: Der einzige Ort, an dem konkrete Implementierungen
/// zusammengesteckt werden (Dependency Inversion Principle).
@MainActor
final class AppContainer {
    private let logDrink: LogDrinkUseCase
    private let fetchDrinks: FetchDrinksUseCase
    private let deleteDrink: DeleteDrinkUseCase
    private let shareDrinks: ShareDrinksUseCase

    init(repository: DrinkRepository) {
        let formatter = GermanShareFormatter()
        logDrink = DefaultLogDrinkUseCase(validator: DefaultDrinkValidator(), writer: repository)
        fetchDrinks = DefaultFetchDrinksUseCase(reader: repository)
        deleteDrink = DefaultDeleteDrinkUseCase(writer: repository)
        shareDrinks = DefaultShareDrinksUseCase(messageFormatter: formatter, summaryFormatter: formatter)
    }

    static func live() -> AppContainer {
        let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return AppContainer(repository: JSONFileDrinkRepository(fileURL: documents.appendingPathComponent("drinks.json")))
    }

    static func preview() -> AppContainer {
        let now = Date()
        return AppContainer(repository: InMemoryDrinkRepository(drinks: [
            Drink(id: UUID(), name: "Cappuccino", category: .coffee, volumeInMilliliters: 200, note: "Im Lieblingscafé", consumedAt: now.addingTimeInterval(-7_200)),
            Drink(id: UUID(), name: "Mineralwasser", category: .water, volumeInMilliliters: 500, note: nil, consumedAt: now)
        ]))
    }

    func makeDrinkListViewModel() -> DrinkListViewModel {
        DrinkListViewModel(fetchDrinks: fetchDrinks, deleteDrink: deleteDrink, shareDrinks: shareDrinks)
    }

    func makeAddDrinkViewModel(onSaved: @escaping (Drink) -> Void) -> AddDrinkViewModel {
        AddDrinkViewModel(logDrink: logDrink, onSaved: onSaved)
    }
}
