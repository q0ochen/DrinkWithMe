package com.drinkwithme.core.social

/**
 * Einladungscode einer Trinkrunde, z. B. „K7MX2QAB“.
 *
 * Acht Zeichen aus einem Alphabet ohne leicht verwechselbare Zeichen
 * (kein I, L, O, 0, 1). Der Server erzeugt die Codes; die App prüft nur das Format.
 */
@JvmInline
value class InviteCode private constructor(val value: String) {
    override fun toString(): String = value

    companion object {
        const val LENGTH = 8
        const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

        /** Normalisiert die Eingabe (Großschreibung, ohne Leerzeichen/Bindestriche) und prüft das Format. */
        fun parse(raw: String): InviteCode? {
            val normalized = raw.uppercase().filterNot { it.isWhitespace() || it == '-' }
            val valid = normalized.length == LENGTH && normalized.all { it in ALPHABET }
            return if (valid) InviteCode(normalized) else null
        }
    }
}
