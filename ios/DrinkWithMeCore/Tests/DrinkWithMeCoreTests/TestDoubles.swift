import Foundation
@testable import DrinkWithMeCore

struct FixedDateProvider: DateProviding {
    let date: Date
    func now() -> Date { date }
}

/// Uhr, die im Test „weiterlaufen“ kann.
final class MutableDateProvider: DateProviding {
    var date: Date
    init(date: Date) { self.date = date }
    func now() -> Date { date }
}

struct FixedIdentifierGenerator: IdentifierGenerating {
    let identifier: UUID
    func makeIdentifier() -> UUID { identifier }
}

enum Fixtures {
    static let utc = TimeZone(identifier: "UTC")!

    static var utcCalendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = utc
        return calendar
    }

    /// 09.11.2026, 14:30:00 UTC
    static let afternoon = Date(timeIntervalSince1970: 1_794_234_600)

    static func drink(
        name: String = "Espresso",
        category: DrinkCategory = .coffee,
        volume: Int = 30,
        note: String? = nil,
        at date: Date = afternoon
    ) -> Drink {
        Drink(id: UUID(), name: name, category: category, volumeInMilliliters: volume, note: note, consumedAt: date)
    }
}
