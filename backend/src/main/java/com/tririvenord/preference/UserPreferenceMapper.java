package com.tririvenord.preference;

import com.tririvenord.common.LanguageCode;
import com.tririvenord.common.Sector;
import com.tririvenord.common.Sector;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface UserPreferenceMapper {
    Optional<UserPreference> findByUserId(@Param("userId") Long userId);

    /**
     * The row every new account starts with.
     *
     * <p>The sector used to be the literal 'north' and there was no city at
     * all, which was true while there was one city whose halves were north
     * and south. With four, that insert violates NOT NULL on municipality_id
     * and every registration fails -- which is what it did, and what only the
     * integration tests caught: nothing mocked ever writes this row.
     */
    void insertDefault(@Param("userId") Long userId,
                       @Param("municipalityId") long municipalityId,
                       @Param("sector") Sector sector);

    void update(
            @Param("userId") Long userId,
            @Param("municipalityId") long municipalityId,
            @Param("sector") Sector sector,
            @Param("languageCode") LanguageCode languageCode,
            @Param("reminderEnabled") boolean reminderEnabled
    );
}
