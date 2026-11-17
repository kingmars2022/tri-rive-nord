package com.tririvenord.preference;

import com.tririvenord.common.LanguageCode;
import com.tririvenord.common.Sector;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface UserPreferenceMapper {
    Optional<UserPreference> findByUserId(@Param("userId") Long userId);

    void insertDefault(@Param("userId") Long userId);

    void update(
            @Param("userId") Long userId,
            @Param("sector") Sector sector,
            @Param("languageCode") LanguageCode languageCode,
            @Param("reminderEnabled") boolean reminderEnabled
    );
}
