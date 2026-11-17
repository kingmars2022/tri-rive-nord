package com.tririvenord.collection.dto;

import com.tririvenord.collection.BinColor;
import com.tririvenord.collection.CollectionType;
import com.tririvenord.common.Sector;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CollectionEventRequest(
        @NotNull LocalDate collectionDate,
        @NotNull Sector sector,
        @NotNull CollectionType collectionType,
        @NotNull BinColor binColor,
        String noteFr,
        String noteEn,
        String noteZh,
        String sourceUrl
) {
}
