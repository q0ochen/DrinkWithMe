import Foundation

public enum DrinkValidationError: Error, Equatable, LocalizedError {
    case emptyName
    case nameTooLong(maximum: Int)
    case invalidVolume(range: ClosedRange<Int>)
    case noteTooLong(maximum: Int)

    public var errorDescription: String? {
        switch self {
        case .emptyName:
            return "Bitte gib einen Namen für dein Getränk ein."
        case .nameTooLong(let maximum):
            return "Der Name darf höchstens \(maximum) Zeichen lang sein."
        case .invalidVolume(let range):
            return "Die Menge muss zwischen \(range.lowerBound) und \(range.upperBound) ml liegen."
        case .noteTooLong(let maximum):
            return "Die Notiz darf höchstens \(maximum) Zeichen lang sein."
        }
    }
}

/// Prüft Benutzereingaben. Eigene Regeln lassen sich als neue
/// Implementierung einstecken, ohne den Use Case zu ändern.
public protocol DrinkValidating {
    func validate(_ draft: DrinkDraft) throws
}

public struct DefaultDrinkValidator: DrinkValidating {
    public let maximumNameLength: Int
    public let allowedVolume: ClosedRange<Int>
    public let maximumNoteLength: Int

    public init(
        maximumNameLength: Int = 50,
        allowedVolume: ClosedRange<Int> = 1...5_000,
        maximumNoteLength: Int = 280
    ) {
        self.maximumNameLength = maximumNameLength
        self.allowedVolume = allowedVolume
        self.maximumNoteLength = maximumNoteLength
    }

    public func validate(_ draft: DrinkDraft) throws {
        let name = draft.name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty else { throw DrinkValidationError.emptyName }
        guard name.count <= maximumNameLength else {
            throw DrinkValidationError.nameTooLong(maximum: maximumNameLength)
        }
        guard allowedVolume.contains(draft.volumeInMilliliters) else {
            throw DrinkValidationError.invalidVolume(range: allowedVolume)
        }
        if let note = draft.note, note.count > maximumNoteLength {
            throw DrinkValidationError.noteTooLong(maximum: maximumNoteLength)
        }
    }
}
