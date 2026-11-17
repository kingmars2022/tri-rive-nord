package com.tririvenord.collection;

import com.tririvenord.common.Sector;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * A recurring collection, stored as a pattern rather than as its occurrences.
 *
 * @param anchorDate       a date the pattern is known to fall on; the weekday and
 *                         the phase of the cycle both come from it
 * @param intervalWeeks    1 for weekly, 2 for biweekly
 * @param validUntil       the last date the pattern may fall on, inclusive, or
 *                         null when it has no known end. Exists because some
 *                         cities change frequency with the season: Sainte-
 *                         Therese collects organics weekly from April to
 *                         November and fortnightly from December to March,
 *                         which is two rules that each stop, not one that
 *                         runs for ever.
 * @param generatedThrough the last date already materialized into
 *                         {@code collection_event} for this rule
 */
public record CollectionScheduleRule(
        Long id,
        Long municipalityId,
        Sector sector,
        CollectionType collectionType,
        BinColor binColor,
        LocalDate anchorDate,
        int intervalWeeks,
        LocalDate generatedThrough,
        String noteFr,
        String noteEn,
        String noteZh,
        String sourceUrl,
        boolean active,
        LocalDate validUntil
) {
    /** A rule with no end date, which is what every rule was before V22. */
    public CollectionScheduleRule(Long id, Long municipalityId, Sector sector, CollectionType collectionType,
                                  BinColor binColor, LocalDate anchorDate, int intervalWeeks,
                                  LocalDate generatedThrough, String noteFr, String noteEn, String noteZh,
                                  String sourceUrl, boolean active) {
        this(id, municipalityId, sector, collectionType, binColor, anchorDate, intervalWeeks,
                generatedThrough, noteFr, noteEn, noteZh, sourceUrl, active, null);
    }

    /**
     * The occurrences falling in {@code (from, through]}.
     *
     * <p>Exclusive at the start because {@code from} is the high-water mark: a
     * date already generated is never generated again, so an occurrence an
     * administrator deleted stays deleted.
     */
    public List<LocalDate> occurrencesAfter(LocalDate from, LocalDate through) {
        List<LocalDate> dates = new ArrayList<>();
        if (validUntil != null && validUntil.isBefore(through)) {
            through = validUntil;
        }
        if (!from.isBefore(through)) {
            return dates;
        }

        long cycleDays = 7L * Math.max(intervalWeeks, 1);
        long sinceAnchor = ChronoUnit.DAYS.between(anchorDate, from);
        // The first on-cycle date strictly after `from`. Clamped at zero so a
        // rule whose pattern starts later than the mark begins at its anchor
        // rather than being projected backwards into dates it never ran on.
        long cycles = Math.max(0, Math.floorDiv(sinceAnchor, cycleDays) + 1);
        LocalDate date = anchorDate.plusDays(cycles * cycleDays);

        while (!date.isAfter(through)) {
            if (date.isAfter(from)) {
                dates.add(date);
            }
            date = date.plusDays(cycleDays);
        }
        return dates;
    }
}
