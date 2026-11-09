import XCTest
@testable import DrinkWithMeCore

final class UseCaseTests: XCTestCase {
    func testLogDrinkTrimsInputAndStoresDrink() throws {
        let repository = InMemoryDrinkRepository()
        let id = UUID()
        let useCase = DefaultLogDrinkUseCase(
            validator: DefaultDrinkValidator(),
            writer: repository,
            dateProvider: FixedDateProvider(date: Fixtures.afternoon),
            identifierGenerator: FixedIdentifierGenerator(identifier: id)
        )

        let drink = try useCase.execute(DrinkDraft(name: "  Cappuccino ", category: .coffee, volumeInMilliliters: 200, note: "  "))

        XCTAssertEqual(drink, Drink(id: id, name: "Cappuccino", category: .coffee, volumeInMilliliters: 200, note: nil, consumedAt: Fixtures.afternoon))
        XCTAssertEqual(try repository.allDrinks(), [drink])
    }

    func testLogDrinkDoesNotStoreInvalidDraft() {
        let repository = InMemoryDrinkRepository()
        let useCase = DefaultLogDrinkUseCase(validator: DefaultDrinkValidator(), writer: repository)

        XCTAssertThrowsError(try useCase.execute(DrinkDraft(name: "", category: .water, volumeInMilliliters: 200)))
        XCTAssertEqual(try repository.allDrinks(), [])
    }

    func testFetchDrinksSortsNewestFirst() throws {
        let older = Fixtures.drink(name: "Tee", at: Fixtures.afternoon.addingTimeInterval(-3_600))
        let newer = Fixtures.drink(name: "Kaffee", at: Fixtures.afternoon)
        let useCase = DefaultFetchDrinksUseCase(reader: InMemoryDrinkRepository(drinks: [older, newer]))

        XCTAssertEqual(try useCase.execute(), [newer, older])
    }

    func testDeleteDrinkRemovesIt() throws {
        let drink = Fixtures.drink()
        let repository = InMemoryDrinkRepository(drinks: [drink])

        try DefaultDeleteDrinkUseCase(writer: repository).execute(id: drink.id)

        XCTAssertEqual(try repository.allDrinks(), [])
    }

    func testTodaySummaryOnlyContainsTodaysDrinks() {
        let today = Fixtures.drink(name: "Wasser", category: .water, volume: 500)
        let yesterday = Fixtures.drink(name: "Bier", category: .beer, volume: 330, at: Fixtures.afternoon.addingTimeInterval(-86_400))
        let useCase = DefaultShareDrinksUseCase(
            messageFormatter: GermanShareFormatter(timeZone: Fixtures.utc),
            summaryFormatter: GermanShareFormatter(timeZone: Fixtures.utc),
            dateProvider: FixedDateProvider(date: Fixtures.afternoon),
            calendar: Fixtures.utcCalendar
        )

        let summary = useCase.todaySummary(from: [today, yesterday])

        XCTAssertTrue(summary.contains("Wasser"))
        XCTAssertFalse(summary.contains("Bier"))
    }
}
