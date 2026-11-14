import XCTest
@testable import DrinkWithMeCore

final class GermanShareFormatterTests: XCTestCase {
    private let formatter = GermanShareFormatter(timeZone: Fixtures.utc)

    func testMessageForSingleDrink() {
        let drink = Fixtures.drink(name: "Rotwein", category: .wine, volume: 150, note: "Mit Freunden")

        XCTAssertEqual(
            formatter.message(for: drink),
            "🍷 Ich trinke gerade Rotwein (150 ml) um 14:30 Uhr. „Mit Freunden“ Trink mit mir! #DrinkWithMe"
        )
    }

    func testSummaryListsDrinksAndTotal() {
        let drinks = [
            Fixtures.drink(name: "Wasser", category: .water, volume: 1_000),
            Fixtures.drink(name: "Espresso", category: .coffee, volume: 500)
        ]

        XCTAssertEqual(
            formatter.summary(for: drinks, on: Fixtures.afternoon),
            """
            Mein Getränke-Tag am 09.11.2026:
            💧 14:30 Wasser – 1 l
            ☕️ 14:30 Espresso – 500 ml
            Gesamt: 1,5 l
            #DrinkWithMe
            """
        )
    }

    func testSummaryWithoutDrinks() {
        XCTAssertEqual(
            formatter.summary(for: [], on: Fixtures.afternoon),
            "Mein Getränke-Tag am 09.11.2026:\nNoch nichts getrunken – wer trinkt mit mir? #DrinkWithMe"
        )
    }
}
