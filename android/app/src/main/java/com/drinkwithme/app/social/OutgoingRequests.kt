package com.drinkwithme.app.social

import java.time.Instant

/**
 * Eigene ausgehende Freundschaftsanfrage – nur mit der maskierten Adresse (z. B. „b***@example.org“). Der Server
 * verrät dem Absender nicht, ob es zu der Adresse ein Konto gibt und wen die Anfrage erreicht (kein Name, kein Bild).
 */
data class OutgoingRequest(val id: String, val emailHint: String, val createdAt: Instant)

/**
 * Ausgehende Anfragen (RPC `list_outgoing_friend_requests`). Eigener kleiner Port neben dem Kern-Port `FriendsService`,
 * dessen `FriendRequest` immer ein Profil der anderen Person hat – das gibt es für ausgehende Anfragen nicht mehr.
 * Zurückziehen weiter über `FriendsService.cancel`.
 */
interface OutgoingRequestsService {
    /** Neueste zuerst. */
    suspend fun outgoingRequests(): List<OutgoingRequest>
}
