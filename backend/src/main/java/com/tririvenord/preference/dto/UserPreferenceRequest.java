package com.tririvenord.preference.dto;

import com.tririvenord.common.LanguageCode;
import com.tririvenord.common.Sector;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;


public record UserPreferenceRequest(
        // Which city these settings are for. Stored with them, because a
        // sector means nothing without one: "north" is a different place in
        // every city that has one.
        @NotBlank String municipality,
        @NotNull Sector sector,
        @NotNull LanguageCode languageCode,
        boolean reminderEnabled
) {
}
