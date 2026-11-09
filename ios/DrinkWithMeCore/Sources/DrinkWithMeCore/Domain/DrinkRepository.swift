import Foundation

/// Lesender Zugriff auf Getränke (Interface Segregation:
/// Konsumenten, die nur lesen, hängen nicht von Schreibmethoden ab).
public protocol DrinkReading {
    func allDrinks() throws -> [Drink]
}

/// Schreibender Zugriff auf Getränke.
public protocol DrinkWriting {
    func save(_ drink: Drink) throws
    func deleteDrink(withID id: UUID) throws
}

/// Vollständiges Repository – jede Implementierung (Datei, Speicher, Cloud …)
/// ist austauschbar (Liskov Substitution / Dependency Inversion).
public typealias DrinkRepository = DrinkReading & DrinkWriting
