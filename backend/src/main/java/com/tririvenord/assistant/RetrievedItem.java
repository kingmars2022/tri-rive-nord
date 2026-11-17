package com.tririvenord.assistant;

import com.tririvenord.common.LanguageCode;
import com.tririvenord.collection.BinColor;
import com.tririvenord.sorting.DestinationType;

/**
 * One sorting-guide entry that the retriever considered relevant, with the
 * relevance score that got it there. The score is carried all the way out to
 * the API response on purpose: an answer a resident cannot trace back to a
 * source is not much use to them, and a score nobody can see is impossible to
 * tune.
 */
public record RetrievedItem(
        Long itemId,
        LanguageCode languageCode,
        String name,
        String instruction,
        String location,
        DestinationType destinationType,
        BinColor binColor,
        String sourceUrl,
        double score
) {
}
