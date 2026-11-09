package com.drinkwithme.core.domain

import java.util.UUID

/** Abstraktion der ID-Erzeugung, damit Use Cases deterministisch testbar sind. */
fun interface IdGenerator {
    fun newId(): String
}

object RandomIdGenerator : IdGenerator {
    override fun newId(): String = UUID.randomUUID().toString()
}
