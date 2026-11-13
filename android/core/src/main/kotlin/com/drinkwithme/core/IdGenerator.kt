package com.drinkwithme.core

import java.util.UUID

/** Erzeugt eindeutige IDs. Als Schnittstelle, damit Use Cases deterministisch testbar sind. */
fun interface IdGenerator {
    fun newId(): String
}

/** Zufällige UUIDs (Version 4) – Standard für eigene Bar-Getränke und Trinkeinträge. */
object RandomIdGenerator : IdGenerator {
    override fun newId(): String = UUID.randomUUID().toString()
}
