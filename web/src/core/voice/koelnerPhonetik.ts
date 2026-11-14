/** Bildet einen Text auf einen Lautcode ab, damit ähnlich klingende Wörter gleich behandelt werden. */
export interface PhoneticEncoder {
  encode(text: string): string;
}

const NO_CODE = "-";

function prepare(text: string): string {
  let letters = "";
  for (const char of text.toLocaleLowerCase("de-DE")) {
    if (char === "ä") letters += "a";
    else if (char === "ö") letters += "o";
    else if (char === "ü") letters += "u";
    else if (char === "ß") letters += "s";
    else if (char >= "a" && char <= "z" && char.length === 1) letters += char;
  }
  return letters;
}

function codeForC(previous: string | undefined, next: string | undefined, isFirst: boolean): string {
  if (isFirst) return next !== undefined && "ahkloqrux".includes(next) ? "4" : "8";
  if (previous !== undefined && "sz".includes(previous)) return "8";
  if (next !== undefined && "ahkoqux".includes(next)) return "4";
  return "8";
}

function codesFor(letter: string, previous: string | undefined, next: string | undefined, isFirst: boolean): string[] {
  switch (letter) {
    case "a":
    case "e":
    case "i":
    case "j":
    case "o":
    case "u":
    case "y":
      return ["0"];
    case "h":
      return [NO_CODE];
    case "b":
      return ["1"];
    case "p":
      return [next === "h" ? "3" : "1"];
    case "d":
    case "t":
      return [next !== undefined && "csz".includes(next) ? "8" : "2"];
    case "f":
    case "v":
    case "w":
      return ["3"];
    case "g":
    case "k":
    case "q":
      return ["4"];
    case "c":
      return [codeForC(previous, next, isFirst)];
    case "x":
      return previous !== undefined && "ckq".includes(previous) ? ["8"] : ["4", "8"];
    case "l":
      return ["5"];
    case "m":
    case "n":
      return ["6"];
    case "r":
      return ["7"];
    case "s":
    case "z":
      return ["8"];
    default:
      return [NO_CODE];
  }
}

/**
 * Kölner Phonetik (Postel 1969), Port von `KoelnerPhonetik`: Buchstaben werden nach den Regeln (inkl. Kontext
 * für C, D/T, P, X) in Ziffern übersetzt, gleiche Folgeziffern zusammengefasst und alle „0“ außer am Anfang
 * entfernt. H erzeugt keinen Code. Nicht-Buchstaben werden ignoriert, Umlaute wie ihre Grundvokale behandelt.
 */
export const koelnerPhonetik: PhoneticEncoder = {
  encode(text: string): string {
    const letters = prepare(text);
    let output = "";
    let lastCode = NO_CODE;
    for (let index = 0; index < letters.length; index++) {
      const previous = index > 0 ? letters[index - 1] : undefined;
      const next = index + 1 < letters.length ? letters[index + 1] : undefined;
      for (const code of codesFor(letters[index], previous, next, index === 0)) {
        if (code === NO_CODE) continue; // H ist „durchsichtig“.
        const keepsZero = output.length === 0; // „0“ bleibt nur als allererster Code erhalten.
        if (code !== lastCode && (code !== "0" || keepsZero)) output += code;
        lastCode = code;
      }
    }
    return output;
  },
};
