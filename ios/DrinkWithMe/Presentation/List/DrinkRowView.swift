import SwiftUI
import DrinkWithMeCore

/// Reine Darstellung eines Getränks – keine Logik.
struct DrinkRowView: View {
    let drink: Drink
    private let volumeFormatter = VolumeFormatter()

    var body: some View {
        HStack(spacing: 12) {
            Text(drink.category.emoji)
                .font(.largeTitle)
            VStack(alignment: .leading, spacing: 4) {
                Text(drink.name).font(.headline)
                Text("\(drink.category.displayName) · \(volumeFormatter.string(fromMilliliters: drink.volumeInMilliliters))")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                if let note = drink.note {
                    Text(note)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                }
            }
            Spacer()
            Text(drink.consumedAt, format: .dateTime.hour().minute())
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .combine)
    }
}
