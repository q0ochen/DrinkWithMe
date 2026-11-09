package com.drinkwithme.app.social.firebase

/**
 * Pfade, Feldnamen und Funktionsnamen laut docs/TRINKRUNDEN_ARCHITEKTUR.md – an genau einer Stelle,
 * damit App, Sicherheitsregeln und Cloud Functions garantiert dieselben Namen verwenden.
 */
internal object FirestoreSchema {
    const val USERS = "users"
    const val TOKENS = "tokens"
    const val GROUPS = "groups"
    const val DRINKS = "drinks"

    /** `users/{uid}` */
    object User {
        const val DISPLAY_NAME = "displayName"
        const val PHOTO_URL = "photoUrl"
        const val UPDATED_AT = "updatedAt"
    }

    /** `users/{uid}/tokens/{fcmToken}` */
    object Token {
        const val PLATFORM = "platform"
        const val UPDATED_AT = "updatedAt"
        const val PLATFORM_ANDROID = "android"
    }

    /** `groups/{groupId}` – schreiben nur die Cloud Functions. */
    object Group {
        const val NAME = "name"
        const val INVITE_CODE = "inviteCode"
        const val OWNER_ID = "ownerId"
        const val MEMBER_IDS = "memberIds"
        const val MEMBERS = "members"
        const val MEMBER_DISPLAY_NAME = "displayName"
    }

    /**
     * `groups/{groupId}/drinks/{authorId}_{drinkId}` – die Dokument-ID beginnt mit der Nutzer-ID des Autors.
     * Die Sicherheitsregeln erzwingen das, damit niemand die ID eines fremden Getränks in einer Runde „besetzt“.
     */
    object GroupDrink {
        const val DRINK_ID = "drinkId"
        const val AUTHOR_ID = "authorId"
        const val AUTHOR_NAME = "authorName"
        const val NAME = "name"
        const val CATEGORY = "category"
        const val VOLUME_IN_MILLILITERS = "volumeInMilliliters"
        const val NOTE = "note"
        const val CONSUMED_AT = "consumedAt"
        const val CREATED_AT = "createdAt"

        /** Dokument-ID eines geteilten Getränks: `"{authorId}_{drinkId}"` (in allen Runden dieselbe). */
        fun documentId(authorId: String, drinkId: String): String = "${authorId}_$drinkId"
    }
}

/** Aufrufbare Cloud Functions (2nd gen) samt Ein- und Ausgabefeldern. */
internal object CloudFunctions {
    const val REGION = "europe-west3"

    const val CREATE_GROUP = "createGroup"
    const val JOIN_GROUP = "joinGroup"
    const val LEAVE_GROUP = "leaveGroup"
    const val DELETE_ACCOUNT = "deleteAccount"

    const val PARAM_NAME = "name"
    const val PARAM_CODE = "code"
    const val PARAM_GROUP_ID = "groupId"

    const val RESULT_GROUP = "group"

    /** Felder von `GroupDto = { id, name, inviteCode, ownerId, members: [{ userId, displayName }] }`. */
    object GroupDto {
        const val ID = "id"
        const val NAME = "name"
        const val INVITE_CODE = "inviteCode"
        const val OWNER_ID = "ownerId"
        const val MEMBERS = "members"
        const val MEMBER_USER_ID = "userId"
        const val MEMBER_DISPLAY_NAME = "displayName"
    }
}
