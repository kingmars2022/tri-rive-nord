package com.tririvenord.preference;

import com.tririvenord.common.LanguageCode;
import com.tririvenord.common.Sector;


public record UserPreference(
        Long userId,
        Sector sector,
        LanguageCode languageCode,
        boolean reminderEnabled
) {
}
