import SwiftUI

@main
@MainActor
struct DrinkWithMeApp: App {
    private let container = AppContainer.live()

    var body: some Scene {
        WindowGroup {
            DrinkListView(
                viewModel: container.makeDrinkListViewModel(),
                makeAddDrinkViewModel: container.makeAddDrinkViewModel
            )
        }
    }
}
