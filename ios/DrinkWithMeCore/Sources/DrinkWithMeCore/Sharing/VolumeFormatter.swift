import Foundation

/// Wandelt Milliliter in gut lesbaren Text um („250 ml“, „1,5 l“).
public struct VolumeFormatter {
    public init() {}

    public func string(fromMilliliters milliliters: Int) -> String {
        guard milliliters >= 1_000 else { return "\(milliliters) ml" }
        let tenthsOfLiter = (milliliters + 50) / 100
        let whole = tenthsOfLiter / 10
        let fraction = tenthsOfLiter % 10
        return fraction == 0 ? "\(whole) l" : "\(whole),\(fraction) l"
    }
}
