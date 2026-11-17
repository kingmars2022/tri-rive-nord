package com.bienvenueblainville.sorting.dto;

import com.bienvenueblainville.collection.BinColor;
import com.bienvenueblainville.sorting.DestinationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SortingItemRequest(
        // The logical item this entry is about, shared across cities. Pattern
        // rather than free text so that "pizza-box" in one city matches
        // "pizza-box" in the next: a key that differs only in punctuation
        // would make the four guides look more different than they are.
        @NotBlank @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*", message = "lowercase words joined by hyphens")
        @Size(max = 80) String itemKey,
        @NotNull DestinationType destinationType,
        @NotNull BinColor binColor,
        String sourceUrl,
        @NotNull @Valid TranslationInput fr,
        @NotNull @Valid TranslationInput en,
        @NotNull @Valid TranslationInput zh,
        List<String> keywordsFr,
        List<String> keywordsEn,
        List<String> keywordsZh
) {
}
