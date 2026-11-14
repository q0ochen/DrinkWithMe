import Foundation

/// Art eines Getränks. Neue Kategorien können ergänzt werden, ohne
/// bestehende Use Cases anzupassen (Open/Closed Principle).
public enum DrinkCategory: String, CaseIterable, Codable, Identifiable, Sendable {
    case water
    case coffee
    case tea
    case juice
    case softDrink
    case beer
    case wine
    case cocktail
    case other

    public var id: String { rawValue }

    public var displayName: String {
        switch self {
        case .water: return "Wasser"
        case .coffee: return "Kaffee"
        case .tea: return "Tee"
        case .juice: return "Saft"
        case .softDrink: return "Softdrink"
        case .beer: return "Bier"
        case .wine: return "Wein"
        case .cocktail: return "Cocktail"
        case .other: return "Sonstiges"
        }
    }

    public var emoji: String {
        switch self {
        case .water: return "💧"
        case .coffee: return "☕️"
        case .tea: return "🍵"
        case .juice: return "🧃"
        case .softDrink: return "🥤"
        case .beer: return "🍺"
        case .wine: return "🍷"
        case .cocktail: return "🍹"
        case .other: return "🥛"
        }
    }
}
