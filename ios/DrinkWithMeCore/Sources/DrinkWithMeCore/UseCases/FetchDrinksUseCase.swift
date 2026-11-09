import Foundation

/// Liefert alle Getränke, neueste zuerst.
public protocol FetchDrinksUseCase {
    func execute() throws -> [Drink]
}

public final class DefaultFetchDrinksUseCase: FetchDrinksUseCase {
    private let reader: DrinkReading

    public init(reader: DrinkReading) {
        self.reader = reader
    }

    public func execute() throws -> [Drink] {
        try reader.allDrinks().sorted { $0.consumedAt > $1.consumedAt }
    }
}
