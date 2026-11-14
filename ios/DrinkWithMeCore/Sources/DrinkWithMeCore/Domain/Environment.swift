import Foundation

/// Abstraktion der Uhrzeit, damit Use Cases deterministisch testbar sind.
public protocol DateProviding {
    func now() -> Date
}

public struct SystemDateProvider: DateProviding {
    public init() {}
    public func now() -> Date { Date() }
}

/// Abstraktion der ID-Erzeugung.
public protocol IdentifierGenerating {
    func makeIdentifier() -> UUID
}

public struct RandomIdentifierGenerator: IdentifierGenerating {
    public init() {}
    public func makeIdentifier() -> UUID { UUID() }
}
