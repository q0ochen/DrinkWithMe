import type { Firestore } from "firebase-admin/firestore";
import type { ProfileReader } from "../groups/groupService";
import { Collections } from "./paths";

/** Liest den Anzeigenamen aus `users/{uid}.displayName`. */
export class FirestoreProfileReader implements ProfileReader {
  constructor(private readonly db: Firestore) {}

  async displayNameOf(userId: string): Promise<string | null> {
    const snapshot = await this.db.collection(Collections.users).doc(userId).get();
    const displayName: unknown = snapshot.get("displayName");
    return typeof displayName === "string" ? displayName : null;
  }
}
