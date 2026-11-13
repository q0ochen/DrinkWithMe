import Foundation

/// Formatiert ein einzelnes Getränk als teilbaren Text.
public protocol DrinkMessageFormatting {
    func message(for drink: Drink) -> String
}

/// Formatiert eine Tagesübersicht als teilbaren Text.
public protocol DrinkSummaryFormatting {
    func summary(for drinks: [Drink], on day: Date) -> String
}
