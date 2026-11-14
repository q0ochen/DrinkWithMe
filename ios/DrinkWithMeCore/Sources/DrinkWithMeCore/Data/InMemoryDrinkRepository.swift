import Foundation

/// Flüchtiges Repository für Tests und SwiftUI-Previews.
public final class InMemoryDrinkRepository: DrinkRepository {
    private var drinks: [UUID: Drink]
    private let lock = NSLock()

    public init(drinks: [Drink] = []) {
        self.drinks = Dictionary(uniqueKeysWithValues: drinks.map { ($0.id, $0) })
    }

    public func allDrinks() throws -> [Drink] {
        lock.lock(); defer { lock.unlock() }
        return Array(drinks.values)
    }

    public func save(_ drink: Drink) throws {
        lock.lock(); defer { lock.unlock() }
        drinks[drink.id] = drink
    }

    public func deleteDrink(withID id: UUID) throws {
        lock.lock(); defer { lock.unlock() }
        drinks[id] = nil
    }
}
