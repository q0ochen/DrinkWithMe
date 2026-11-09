import Foundation

/// Erfasst ein neues Getränk – immer mit dem aktuellen Zeitpunkt.
public protocol LogDrinkUseCase {
    @discardableResult
    func execute(_ draft: DrinkDraft) throws -> Drink
}

public final class DefaultLogDrinkUseCase: LogDrinkUseCase {
    private let validator: DrinkValidating
    private let writer: DrinkWriting
    private let dateProvider: DateProviding
    private let identifierGenerator: IdentifierGenerating

    public init(
        validator: DrinkValidating,
        writer: DrinkWriting,
        dateProvider: DateProviding = SystemDateProvider(),
        identifierGenerator: IdentifierGenerating = RandomIdentifierGenerator()
    ) {
        self.validator = validator
        self.writer = writer
        self.dateProvider = dateProvider
        self.identifierGenerator = identifierGenerator
    }

    @discardableResult
    public func execute(_ draft: DrinkDraft) throws -> Drink {
        try validator.validate(draft)

        let trimmedNote = draft.note?.trimmingCharacters(in: .whitespacesAndNewlines)
        let drink = Drink(
            id: identifierGenerator.makeIdentifier(),
            name: draft.name.trimmingCharacters(in: .whitespacesAndNewlines),
            category: draft.category,
            volumeInMilliliters: draft.volumeInMilliliters,
            note: (trimmedNote?.isEmpty ?? true) ? nil : trimmedNote,
            consumedAt: dateProvider.now()
        )
        try writer.save(drink)
        return drink
    }
}
