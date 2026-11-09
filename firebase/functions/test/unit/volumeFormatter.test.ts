import { describe, expect, it } from "vitest";
import { formatVolume } from "../../src/domain/volumeFormatter";

describe("formatVolume (wie VolumeFormatter im Kotlin-Kern)", () => {
  it.each([
    [250, "250 ml"],
    [1, "1 ml"],
    [999, "999 ml"],
    [1_000, "1 l"],
    [1_049, "1 l"],
    [1_050, "1,1 l"],
    [1_250, "1,3 l"],
    [1_500, "1,5 l"],
    [2_480, "2,5 l"],
    [1_950, "2 l"],
    [5_000, "5 l"],
  ])("%i ml → %s", (milliliters, expected) => {
    expect(formatVolume(milliliters)).toBe(expected);
  });
});
