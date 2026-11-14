package com.drinkwithme.core.bar

/** Herkunft der Werte (Portion, Vol.-%, Zucker) eines Bar-Getränks. */
enum class ValueSource {
    /** Vorschlagswert aus dem [DefaultBarCatalog]. */
    DEFAULT,

    /** Vom Nutzer eingegeben oder geändert. */
    USER,
}
