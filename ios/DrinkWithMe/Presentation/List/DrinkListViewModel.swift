import Foundation
import DrinkWithMeCore

/// Präsentationslogik der Getränkeliste – kennt nur Use-Case-Protokolle.
@MainActor
final class DrinkListViewModel: ObservableObject {
    @Published private(set) var drinks: [Drink] = []
    @Published var errorMessage: String?

    private let fetchDrinks: FetchDrinksUseCase
    private let deleteDrink: DeleteDrinkUseCase
    private let shareDrinks: ShareDrinksUseCase

    init(fetchDrinks: FetchDrinksUseCase, deleteDrink: DeleteDrinkUseCase, shareDrinks: ShareDrinksUseCase) {
        self.fetchDrinks = fetchDrinks
        self.deleteDrink = deleteDrink
        self.shareDrinks = shareDrinks
    }

    var todaySummary: String {
        shareDrinks.todaySummary(from: drinks)
    }

    func load() {
        do {
            drinks = try fetchDrinks.execute()
        } catch {
            errorMessage = "Getränke konnten nicht geladen werden: \(error.localizedDescription)"
        }
    }

    func delete(at offsets: IndexSet) {
        do {
            for index in offsets {
                try deleteDrink.execute(id: drinks[index].id)
            }
        } catch {
            errorMessage = "Getränk konnte nicht gelöscht werden: \(error.localizedDescription)"
        }
        load()
    }

    func shareMessage(for drink: Drink) -> String {
        shareDrinks.message(for: drink)
    }
}
