import Foundation

/// Ein getrunkenes Getränk – reines Datenmodell ohne Logik (Single Responsibility).
public struct Drink: Identifiable, Codable, Equatable, Hashable, Sendable {
    public let id: UUID
    public let name: String
    public let category: DrinkCategory
    public let volumeInMilliliters: Int
    public let note: String?
    public let consumedAt: Date

    public init(
        id: UUID,
        name: String,
        category: DrinkCategory,
        volumeInMilliliters: Int,
        note: String?,
        consumedAt: Date
    ) {
        self.id = id
        self.name = name
        self.category = category
        self.volumeInMilliliters = volumeInMilliliters
        self.note = note
        self.consumedAt = consumedAt
    }
}

/// Benutzereingabe, bevor daraus ein gültiges `Drink` wird.
///
/// Enthält bewusst keinen Zeitpunkt: Ein Getränk wird immer mit dem
/// aktuellen Zeitpunkt („jetzt“) erfasst, den der Use Case setzt.
public struct DrinkDraft: Equatable, Sendable {
    public var name: String
    public var category: DrinkCategory
    public var volumeInMilliliters: Int
    public var note: String?

    public init(
        name: String,
        category: DrinkCategory,
        volumeInMilliliters: Int,
        note: String? = nil
    ) {
        self.name = name
        self.category = category
        self.volumeInMilliliters = volumeInMilliliters
        self.note = note
    }
}
