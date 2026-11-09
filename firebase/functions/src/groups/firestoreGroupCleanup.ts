import type { Firestore } from "firebase-admin/firestore";
import { Collections } from "../firestore/paths";
import type { GroupCleanup } from "./groupCleanup";

/** Löscht die Getränke einer aufgelösten Runde mit `recursiveDelete` (BulkWriter des Admin SDK). */
export class FirestoreGroupCleanup implements GroupCleanup {
  constructor(private readonly db: Firestore) {}

  async deleteDrinksOf(groupId: string): Promise<boolean> {
    const groupRef = this.db.collection(Collections.groups).doc(groupId);
    if ((await groupRef.get()).exists) return false;
    await this.db.recursiveDelete(groupRef.collection(Collections.drinks));
    return true;
  }
}
