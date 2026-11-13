import XCTest
@testable import DrinkWithMeCore

final class JSONFileDrinkRepositoryTests: XCTestCase {
    private var directory: URL!

    override func setUpWithError() throws {
        directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
    }

    override func tearDownWithError() throws {
        try? FileManager.default.removeItem(at: directory)
    }

    func testPersistsAcrossInstances() throws {
        let fileURL = directory.appendingPathComponent("drinks.json")
        let drink = Fixtures.drink(note: "lecker")

        try JSONFileDrinkRepository(fileURL: fileURL).save(drink)

        XCTAssertEqual(try JSONFileDrinkRepository(fileURL: fileURL).allDrinks(), [drink])
    }

    func testDeleteRemovesDrinkFromFile() throws {
        let repository = JSONFileDrinkRepository(fileURL: directory.appendingPathComponent("drinks.json"))
        let keep = Fixtures.drink(name: "Tee")
        let remove = Fixtures.drink(name: "Cola")
        try repository.save(keep)
        try repository.save(remove)

        try repository.deleteDrink(withID: remove.id)

        XCTAssertEqual(try repository.allDrinks(), [keep])
    }

    func testReturnsEmptyListWhenFileIsMissing() throws {
        XCTAssertEqual(try JSONFileDrinkRepository(fileURL: directory.appendingPathComponent("none.json")).allDrinks(), [])
    }
}
