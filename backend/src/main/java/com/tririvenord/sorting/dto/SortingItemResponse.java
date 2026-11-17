package com.tririvenord.sorting.dto;

import com.tririvenord.collection.BinColor;
import com.tririvenord.sorting.DestinationType;

import java.util.List;

public record SortingItemResponse(
        Long id,
        DestinationType destinationType,
        BinColor binColor,
        String sourceUrl,
        TranslationView fr,
        TranslationView en,
        TranslationView zh,
        List<String> keywordsFr,
        List<String> keywordsEn,
        List<String> keywordsZh
) {
}
