import type { Firestore } from "firebase-admin/firestore";
import { parseGroupState } from "../domain/group";
import type { GroupState } from "../domain/group";
import type { GroupLookup } from "../notifications/drinkNotifier";
import { Collections } from "./paths";

/** Liest `groups/{groupId}`. */
export class FirestoreGroupLookup implements GroupLookup {
  constructor(private readonly db: Firestore) {}

  async find(groupId: string): Promise<GroupState | null> {
    const snapshot = await this.db.collection(Collections.groups).doc(groupId).get();
    return snapshot.exists ? parseGroupState(snapshot.data()) : null;
  }
}
