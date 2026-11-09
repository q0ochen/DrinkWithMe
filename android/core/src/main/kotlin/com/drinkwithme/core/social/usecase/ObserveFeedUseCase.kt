package com.drinkwithme.core.social.usecase

import com.drinkwithme.core.social.FeedEntry
import com.drinkwithme.core.social.FeedReader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Liefert die geteilten Getränke einer Trinkrunde, neueste zuerst. */
interface ObserveFeedUseCase {
    operator fun invoke(groupId: String): Flow<List<FeedEntry>>
}

class DefaultObserveFeedUseCase(
    private val reader: FeedReader,
    private val limit: Int = DEFAULT_LIMIT,
) : ObserveFeedUseCase {
    override fun invoke(groupId: String): Flow<List<FeedEntry>> =
        reader.observeFeed(groupId, limit).map { entries -> entries.sortedByDescending { it.consumedAt } }

    companion object {
        const val DEFAULT_LIMIT = 100
    }
}
