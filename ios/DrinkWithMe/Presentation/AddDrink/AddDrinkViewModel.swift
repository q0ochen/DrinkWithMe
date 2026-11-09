import Foundation
import DrinkWithMeCore

/// Hält den Formularzustand und delegiert das Speichern an den Use Case.
@MainActor
final class AddDrinkViewModel: ObservableObject {
    static let volumePresets = [30, 100, 200, 250, 330, 500, 1_000]

    @Published var name = ""
    @Published var category: DrinkCategory = .water
    @Published var volumeInMilliliters = 250
    @Published var note = ""
    @Published var consumedAt = Date()
    @Published private(set) var errorMessage: String?

    private let logDrink: LogDrinkUseCase
    private let onSaved: (Drink) -> Void

    init(logDrink: LogDrinkUseCase, onSaved: @escaping (Drink) -> Void) {
        self.logDrink = logDrink
        self.onSaved = onSaved
    }

    func save() {
        let draft = DrinkDraft(
            name: name,
            category: category,
            volumeInMilliliters: volumeInMilliliters,
            note: note,
            consumedAt: consumedAt
        )
        do {
            let drink = try logDrink.execute(draft)
            errorMessage = nil
            onSaved(drink)
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
