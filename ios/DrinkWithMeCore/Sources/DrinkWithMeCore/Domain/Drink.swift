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
public struct DrinkDraft: Equatable, Sendable {
    public var name: String
    public var category: DrinkCategory
    public var volumeInMilliliters: Int
    public var note: String?
    public var consumedAt: Date?

    public init(
        name: String,
        category: DrinkCategory,
        volumeInMilliliters: Int,
        note: String? = nil,
        consumedAt: Date? = nil
    ) {
        self.name = name
        self.category = category
        self.volumeInMilliliters = volumeInMilliliters
        self.note = note
        self.consumedAt = consumedAt
    }
}
