import { describe, expect, it } from "vitest";
import { FirebaseAuthUserDeleter } from "../../src/accounts/firebaseAuthUserDeleter";
import type { AuthAdmin } from "../../src/accounts/firebaseAuthUserDeleter";

class FakeAuth implements AuthAdmin {
  readonly deleted: string[] = [];

  constructor(private readonly failure?: Error) {}

  deleteUser(uid: string): Promise<void> {
    this.deleted.push(uid);
    return this.failure === undefined ? Promise.resolve() : Promise.reject(this.failure);
  }
}

describe("FirebaseAuthUserDeleter", () => {
  it("löscht den Nutzer in Firebase Auth", async () => {
    const auth = new FakeAuth();

    await new FirebaseAuthUserDeleter(auth).deleteUser("bob");

    expect(auth.deleted).toEqual(["bob"]);
  });

  it("ist idempotent: ein bereits gelöschter Nutzer ist kein Fehler", async () => {
    const auth = new FakeAuth(Object.assign(new Error("no user"), { code: "auth/user-not-found" }));

    await expect(new FirebaseAuthUserDeleter(auth).deleteUser("bob")).resolves.toBeUndefined();
  });

  it("reicht andere Fehler weiter", async () => {
    const failure = Object.assign(new Error("kaputt"), { code: "auth/internal-error" });

    const withoutCode = new Error("ohne Code");

    await expect(new FirebaseAuthUserDeleter(new FakeAuth(failure)).deleteUser("bob")).rejects.toBe(failure);
    await expect(new FirebaseAuthUserDeleter(new FakeAuth(withoutCode)).deleteUser("bob")).rejects.toBe(withoutCode);
  });
});
