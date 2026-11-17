package com.tririvenord.preference.dto;

import com.tririvenord.common.LanguageCode;
import com.tririvenord.common.Sector;
import jakarta.validation.constraints.NotNull;


public record UserPreferenceRequest(
        @NotNull Sector sector,
        @NotNull LanguageCode languageCode,
        boolean reminderEnabled
) {
}
