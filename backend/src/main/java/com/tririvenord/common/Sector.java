package com.tririvenord.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A part of one city that gets its own collection days.
 *
 * <p>This was an enum of {@code north}, {@code south} and {@code all}, which
 * described Blainville exactly: the two halves of boulevard de la Seigneurie,
 * plus the city-wide case. That is a fact about one municipality, and an enum
 * is the one place a fact about one municipality cannot live once there are
 * four -- a city whose calendar divides it some other way could not be read
 * out of the database at all, because the value would not be a constant.
 *
 * <p>So the valid set moved to {@code municipality_sector}, one row per
 * sector per city, and this became a value type over the code. What it still
 * does is make the code a type rather than a bare String, so a sector cannot
 * be passed where a bin colour is expected, and so the format is checked once
 * here instead of at every boundary.
 *
 * <p>Membership is deliberately <em>not</em> checked here: whether
 * {@code secteur-2} exists depends on which city is being asked about, and a
 * value type has no business running a query. That check belongs where the
 * city is known, which is the service that already resolved it.
 */
public record Sector(String code) {
    // Declared before ALL on purpose. Static fields initialise in source
    // order, and ALL runs the compact constructor, which reads these: with
    // the constant first, VALID is still null when it is validated and the
    // class fails to initialise at all.
    private static final Pattern VALID = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    private static final int MAX_LENGTH = 40;

    /** Covers the whole territory. The one code every city has. */
    public static final Sector ALL = new Sector("all");

    public Sector {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("A sector code is required");
        }
        code = code.trim().toLowerCase(Locale.ROOT);
        if (code.length() > MAX_LENGTH || !VALID.matcher(code).matches()) {
            throw new IllegalArgumentException(
                    "A sector code is lowercase words joined by hyphens: " + code);
        }
    }

    /**
     * Parses one, for Jackson and for Spring's parameter binding.
     *
     * <p>Both used to get this for free from the enum, and losing it silently
     * is how a value type like this breaks a JSON API: the record would
     * otherwise serialise as {@code {"code":"north"}} where every existing
     * client, and every row of seeded data, says {@code "north"}.
     */
    @JsonCreator
    public static Sector of(String code) {
        return new Sector(code);
    }

    @JsonValue
    @Override
    public String code() {
        return code;
    }

    public boolean isAll() {
        return ALL.code.equals(code);
    }

    @Override
    public String toString() {
        return code;
    }
}
