package com.tririvenord.collection.dto;

import com.tririvenord.common.Sector;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * A closed day as an administrator supplies it.
 *
 * <p>All three names are required, for the same reason a sorting entry cannot
 * be saved without a French translation: the explanation appended to a moved
 * collection is shown in the resident's own language, and a missing one would
 * surface as a blank reason on the day it matters most.
 *
 * <p>{@code shiftDays} is bounded in three places - here, by a CHECK
 * constraint (V14, widened in V22), and in {@code HolidayShift}. Negative
 * moves a collection earlier, which is what Sainte-Therese does with a Friday
 * holiday; zero is refused everywhere, because a shift of zero leaves the
 * collection on the closed day, and a constraint that lives in only one of
 * those places is one refactor away from living in none.
 */
public record CollectionHolidayRequest(
        @NotNull LocalDate holidayDate,
        @NotBlank @Size(max = 200) String nameFr,
        @NotBlank @Size(max = 200) String nameEn,
        @NotBlank @Size(max = 200) String nameZh,
        @Min(-7) @Max(7) int shiftDays,
        @NotNull Sector sector,
        @Size(max = 1000) String sourceUrl,
        boolean active
) {
    @AssertTrue(message = "shiftDays must not be 0")
    public boolean isShiftNonZero() {
        return shiftDays != 0;
    }
}
