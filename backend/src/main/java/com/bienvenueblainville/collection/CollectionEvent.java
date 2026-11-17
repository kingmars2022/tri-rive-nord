package com.bienvenueblainville.collection;

import com.bienvenueblainville.common.Sector;

import java.time.LocalDate;

public record CollectionEvent(
        Long id,
        Long municipalityId,
        LocalDate collectionDate,
        Sector sector,
        CollectionType collectionType,
        BinColor binColor,
        String noteFr,
        String noteEn,
        String noteZh,
        String sourceUrl
) {
}

