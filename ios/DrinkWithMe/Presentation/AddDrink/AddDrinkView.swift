import SwiftUI
import DrinkWithMeCore

@MainActor
struct AddDrinkView: View {
    @StateObject private var viewModel: AddDrinkViewModel
    @Environment(\.dismiss) private var dismiss

    init(viewModel: @autoclosure @escaping () -> AddDrinkViewModel) {
        _viewModel = StateObject(wrappedValue: viewModel())
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Getränk") {
                    TextField("Name, z. B. Cappuccino", text: $viewModel.name)
                    Picker("Kategorie", selection: $viewModel.category) {
                        ForEach(DrinkCategory.allCases) { category in
                            Text("\(category.emoji) \(category.displayName)").tag(category)
                        }
                    }
                }

                Section("Menge") {
                    Stepper(
                        "\(viewModel.volumeInMilliliters) ml",
                        value: $viewModel.volumeInMilliliters,
                        in: 10...5_000,
                        step: 10
                    )
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack {
                            ForEach(AddDrinkViewModel.volumePresets, id: \.self) { preset in
                                Button("\(preset) ml") { viewModel.volumeInMilliliters = preset }
                                    .buttonStyle(.bordered)
                            }
                        }
                    }
                }

                Section {
                    TextField("Notiz (optional)", text: $viewModel.note, axis: .vertical)
                        .lineLimit(1...4)
                } header: {
                    Text("Notiz")
                } footer: {
                    Text("Das Getränk wird mit dem aktuellen Zeitpunkt gespeichert.")
                }

                if let errorMessage = viewModel.errorMessage {
                    Section {
                        Text(errorMessage).foregroundStyle(.red)
                    }
                }
            }
            .navigationTitle("Neues Getränk")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Abbrechen") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Speichern", action: viewModel.save)
                }
            }
        }
    }
}
