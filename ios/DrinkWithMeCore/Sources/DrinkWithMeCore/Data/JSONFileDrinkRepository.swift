import Foundation

/// Speichert Getränke dauerhaft als JSON-Datei auf dem Gerät.
public final class JSONFileDrinkRepository: DrinkRepository {
    private let fileURL: URL
    private let fileManager: FileManager
    private let encoder: JSONEncoder
    private let decoder: JSONDecoder
    private let lock = NSLock()

    public init(fileURL: URL, fileManager: FileManager = .default) {
        self.fileURL = fileURL
        self.fileManager = fileManager

        encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]

        decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
    }

    public func allDrinks() throws -> [Drink] {
        lock.lock(); defer { lock.unlock() }
        return try load()
    }

    public func save(_ drink: Drink) throws {
        lock.lock(); defer { lock.unlock() }
        var drinks = try load()
        if let index = drinks.firstIndex(where: { $0.id == drink.id }) {
            drinks[index] = drink
        } else {
            drinks.append(drink)
        }
        try persist(drinks)
    }

    public func deleteDrink(withID id: UUID) throws {
        lock.lock(); defer { lock.unlock() }
        try persist(try load().filter { $0.id != id })
    }

    private func load() throws -> [Drink] {
        guard fileManager.fileExists(atPath: fileURL.path) else { return [] }
        let data = try Data(contentsOf: fileURL)
        guard !data.isEmpty else { return [] }
        return try decoder.decode([Drink].self, from: data)
    }

    private func persist(_ drinks: [Drink]) throws {
        try fileManager.createDirectory(
            at: fileURL.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )
        try encoder.encode(drinks).write(to: fileURL, options: .atomic)
    }
}
