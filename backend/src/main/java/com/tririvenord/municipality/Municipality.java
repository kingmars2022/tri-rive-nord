package com.tririvenord.municipality;

/**
 * One city the application covers.
 *
 * <p>The two verified flags are not decoration. A city exists here as soon as
 * it is worth offering, but its calendar and its sorting guide each arrive
 * from a document somebody has to read, and until that has happened the
 * application has to say so. An unloaded city rendered as an ordinary city
 * with no collections this week is the same failure the calendar defect was:
 * confidently wrong rather than visibly incomplete.
 */
public record Municipality(
        Long id,
        String slug,
        String name,
        String province,
        String sourceUrl,
        boolean calendarVerified,
        boolean guideVerified,
        // The town's own set-out rule (V35); null where it prints none.
        java.time.LocalTime binsOutFrom,
        java.time.LocalTime binsOutBy,
        java.time.LocalTime binsInBy
) {
}
