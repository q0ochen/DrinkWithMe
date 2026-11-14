import Foundation

/// Entfernt ein Getränk.
public protocol DeleteDrinkUseCase {
    func execute(id: UUID) throws
}

public final class DefaultDeleteDrinkUseCase: DeleteDrinkUseCase {
    private let writer: DrinkWriting

    public init(writer: DrinkWriting) {
        self.writer = writer
    }

    public func execute(id: UUID) throws {
        try writer.deleteDrink(withID: id)
    }
}
