import Foundation

/// Erzeugt teilbare Texte für einzelne Getränke oder den heutigen Tag.
public protocol ShareDrinksUseCase {
    func message(for drink: Drink) -> String
    func todaySummary(from drinks: [Drink]) -> String
}

public final class DefaultShareDrinksUseCase: ShareDrinksUseCase {
    private let messageFormatter: DrinkMessageFormatting
    private let summaryFormatter: DrinkSummaryFormatting
    private let dateProvider: DateProviding
    private let calendar: Calendar

    public init(
        messageFormatter: DrinkMessageFormatting,
        summaryFormatter: DrinkSummaryFormatting,
        dateProvider: DateProviding = SystemDateProvider(),
        calendar: Calendar = .current
    ) {
        self.messageFormatter = messageFormatter
        self.summaryFormatter = summaryFormatter
        self.dateProvider = dateProvider
        self.calendar = calendar
    }

    public func message(for drink: Drink) -> String {
        messageFormatter.message(for: drink)
    }

    public func todaySummary(from drinks: [Drink]) -> String {
        let today = dateProvider.now()
        let todaysDrinks = drinks
            .filter { calendar.isDate($0.consumedAt, inSameDayAs: today) }
            .sorted { $0.consumedAt < $1.consumedAt }
        return summaryFormatter.summary(for: todaysDrinks, on: today)
    }
}
