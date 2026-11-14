import Foundation

/// Deutschsprachige Share-Texte. Weitere Sprachen oder Stile (z. B. nur Emojis)
/// werden als neue Formatter ergänzt – bestehender Code bleibt unverändert.
public struct GermanShareFormatter: DrinkMessageFormatting, DrinkSummaryFormatting {
    public static let hashtag = "#DrinkWithMe"

    private let volumeFormatter: VolumeFormatter
    private let timeFormatter: DateFormatter
    private let dayFormatter: DateFormatter

    public init(volumeFormatter: VolumeFormatter = VolumeFormatter(), timeZone: TimeZone = .current) {
        self.volumeFormatter = volumeFormatter

        let locale = Locale(identifier: "de_DE")
        timeFormatter = DateFormatter()
        timeFormatter.locale = locale
        timeFormatter.timeZone = timeZone
        timeFormatter.dateFormat = "HH:mm"

        dayFormatter = DateFormatter()
        dayFormatter.locale = locale
        dayFormatter.timeZone = timeZone
        dayFormatter.dateFormat = "dd.MM.yyyy"
    }

    public func message(for drink: Drink) -> String {
        var text = "\(drink.category.emoji) Ich trinke gerade \(drink.name) "
            + "(\(volumeFormatter.string(fromMilliliters: drink.volumeInMilliliters))) "
            + "um \(timeFormatter.string(from: drink.consumedAt)) Uhr."
        if let note = drink.note {
            text += " „\(note)“"
        }
        return text + " Trink mit mir! \(Self.hashtag)"
    }

    public func summary(for drinks: [Drink], on day: Date) -> String {
        let header = "Mein Getränke-Tag am \(dayFormatter.string(from: day)):"
        guard !drinks.isEmpty else {
            return "\(header)\nNoch nichts getrunken – wer trinkt mit mir? \(Self.hashtag)"
        }
        let lines = drinks.map { drink in
            "\(drink.category.emoji) \(timeFormatter.string(from: drink.consumedAt)) "
                + "\(drink.name) – \(volumeFormatter.string(fromMilliliters: drink.volumeInMilliliters))"
        }
        let total = drinks.reduce(0) { $0 + $1.volumeInMilliliters }
        return ([header] + lines + [
            "Gesamt: \(volumeFormatter.string(fromMilliliters: total))",
            Self.hashtag
        ]).joined(separator: "\n")
    }
}
