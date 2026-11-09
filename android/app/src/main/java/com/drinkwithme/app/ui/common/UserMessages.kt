package com.drinkwithme.app.ui.common

import com.drinkwithme.app.social.SocialServiceException
import com.drinkwithme.core.domain.DrinkValidationException
import com.drinkwithme.core.social.GroupNotFoundException
import com.drinkwithme.core.social.SocialValidationException

/**
 * Meldung für die Oberfläche: Eigene Ausnahmen tragen bereits einen deutschen Text;
 * bei allen anderen (technischen, oft englischen) Fehlern wird [fallback] angezeigt.
 */
internal fun Throwable.userMessage(fallback: String): String = when (this) {
    is SocialServiceException,
    is SocialValidationException,
    is GroupNotFoundException,
    is DrinkValidationException,
    -> message ?: fallback
    else -> fallback
}
