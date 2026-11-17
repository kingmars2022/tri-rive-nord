package com.bienvenueblainville.sorting;

import com.bienvenueblainville.collection.BinColor;

/**
 * One entry of one city's sorting guide.
 *
 * @param itemKey a stable identifier for the logical item, the same in every
 *                city that has an entry for it. The guides are stored per
 *                city rather than inherited from a shared base, because the
 *                rules really do differ; this key is what lets the four be
 *                compared, so a city that disagrees with the others is a
 *                query rather than something somebody notices by accident.
 */
public record SortingItem(
        Long id,
        Long municipalityId,
        String itemKey,
        DestinationType destinationType,
        BinColor binColor,
        String sourceUrl
) {
}
