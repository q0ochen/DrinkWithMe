import type { Firestore } from "firebase-admin/firestore";
import type { DeviceToken, DeviceTokenStore } from "../notifications/ports";
import { Collections } from "./paths";

/** Höchstzahl an Schreibvorgängen pro Batch. */
const BATCH_LIMIT = 500;

/** Geräte-Tokens unter `users/{uid}/tokens/{fcmToken}` (Dokument-ID = Token). */
export class FirestoreDeviceTokenStore implements DeviceTokenStore {
  constructor(private readonly db: Firestore) {}

  async newestTokensOf(userId: string, limit: number): Promise<string[]> {
    const snapshot = await this.tokenCollection(userId).orderBy("updatedAt", "desc").limit(limit).get();
    return snapshot.docs.map((doc) => doc.id);
  }

  async remove(tokens: readonly DeviceToken[]): Promise<void> {
    for (let start = 0; start < tokens.length; start += BATCH_LIMIT) {
      const batch = this.db.batch();
      for (const { userId, token } of tokens.slice(start, start + BATCH_LIMIT)) {
        batch.delete(this.tokenCollection(userId).doc(token));
      }
      await batch.commit();
    }
  }

  private tokenCollection(userId: string) {
    return this.db.collection(Collections.users).doc(userId).collection(Collections.tokens);
  }
}
