import SwiftUI
import DrinkWithMeCore

@MainActor
struct DrinkListView: View {
    @StateObject private var viewModel: DrinkListViewModel
    private let makeAddDrinkViewModel: @MainActor (@escaping (Drink) -> Void) -> AddDrinkViewModel
    @State private var isAddingDrink = false

    init(
        viewModel: @autoclosure @escaping () -> DrinkListViewModel,
        makeAddDrinkViewModel: @escaping @MainActor (@escaping (Drink) -> Void) -> AddDrinkViewModel
    ) {
        _viewModel = StateObject(wrappedValue: viewModel())
        self.makeAddDrinkViewModel = makeAddDrinkViewModel
    }

    var body: some View {
        NavigationStack {
            Group {
                if viewModel.drinks.isEmpty {
                    EmptyDrinksView()
                } else {
                    drinkList
                }
            }
            .navigationTitle("Drink With Me")
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    ShareLink(item: viewModel.todaySummary) {
                        Label("Tag teilen", systemImage: "square.and.arrow.up")
                    }
                }
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        isAddingDrink = true
                    } label: {
                        Label("Getränk hinzufügen", systemImage: "plus")
                    }
                }
            }
            .sheet(isPresented: $isAddingDrink) {
                AddDrinkView(viewModel: makeAddDrinkViewModel { _ in
                    isAddingDrink = false
                    viewModel.load()
                })
            }
            .alert(
                "Fehler",
                isPresented: Binding(
                    get: { viewModel.errorMessage != nil },
                    set: { if !$0 { viewModel.errorMessage = nil } }
                ),
                actions: { Button("OK", role: .cancel) {} },
                message: { Text(viewModel.errorMessage ?? "") }
            )
            .onAppear(perform: viewModel.load)
        }
    }

    private var drinkList: some View {
        List {
            ForEach(viewModel.drinks) { drink in
                DrinkRowView(drink: drink)
                    .swipeActions(edge: .leading) {
                        ShareLink(item: viewModel.shareMessage(for: drink)) {
                            Label("Teilen", systemImage: "square.and.arrow.up")
                        }
                        .tint(.blue)
                    }
                    .contextMenu {
                        ShareLink(item: viewModel.shareMessage(for: drink)) {
                            Label("Teilen", systemImage: "square.and.arrow.up")
                        }
                    }
            }
            .onDelete(perform: viewModel.delete)
        }
    }
}

private struct EmptyDrinksView: View {
    var body: some View {
        VStack(spacing: 12) {
            Text("🥂").font(.system(size: 64))
            Text("Noch keine Getränke").font(.title2.bold())
            Text("Tippe auf + und teile, was du gerade trinkst.")
                .font(.body)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding()
    }
}

#Preview {
    let container = AppContainer.preview()
    return DrinkListView(
        viewModel: container.makeDrinkListViewModel(),
        makeAddDrinkViewModel: container.makeAddDrinkViewModel
    )
}
