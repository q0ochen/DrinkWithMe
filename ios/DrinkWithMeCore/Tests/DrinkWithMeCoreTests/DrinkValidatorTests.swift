import XCTest
@testable import DrinkWithMeCore

final class DrinkValidatorTests: XCTestCase {
    private let validator = DefaultDrinkValidator()

    func testAcceptsValidDraft() {
        XCTAssertNoThrow(try validator.validate(DrinkDraft(name: "Tee", category: .tea, volumeInMilliliters: 250)))
    }

    func testRejectsBlankName() {
        XCTAssertThrowsError(try validator.validate(DrinkDraft(name: "   ", category: .tea, volumeInMilliliters: 250))) {
            XCTAssertEqual($0 as? DrinkValidationError, .emptyName)
        }
    }

    func testRejectsTooLongName() {
        let draft = DrinkDraft(name: String(repeating: "a", count: 51), category: .tea, volumeInMilliliters: 250)
        XCTAssertThrowsError(try validator.validate(draft)) {
            XCTAssertEqual($0 as? DrinkValidationError, .nameTooLong(maximum: 50))
        }
    }

    func testRejectsVolumeOutOfRange() {
        for volume in [0, -10, 5_001] {
            XCTAssertThrowsError(try validator.validate(DrinkDraft(name: "Wasser", category: .water, volumeInMilliliters: volume)))
        }
    }

    func testRejectsTooLongNote() {
        let draft = DrinkDraft(name: "Wasser", category: .water, volumeInMilliliters: 200, note: String(repeating: "x", count: 281))
        XCTAssertThrowsError(try validator.validate(draft)) {
            XCTAssertEqual($0 as? DrinkValidationError, .noteTooLong(maximum: 280))
        }
    }
}
