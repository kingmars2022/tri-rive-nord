package com.tririvenord.assistant;

import com.tririvenord.common.LanguageCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Reads one city's sorting guide, in one language, for {@link SortingSearch}
 * to rank. No scoring here: see {@link SortingSearchIndex} for why it moved
 * out of the database.
 *
 * <p>Both reads take the city and the language, and neither has a default:
 * sorting rules are local, so a search that spans cities can return an entry
 * that is correct somewhere and wrong for the resident asking. The refusal
 * depends on it too - this city having no entry is a refusal even when
 * another city has one.
 */
@Mapper
public interface SortingSearchMapper {
    /** Every entry of the city that has a translation in this language. */
    List<RetrievedItem> findEntries(
            @Param("municipalityId") long municipalityId,
            @Param("languageCode") LanguageCode languageCode
    );

    /** The curated keywords of those entries, in this language. */
    List<SortingSearchIndex.Keyword> findKeywords(
            @Param("municipalityId") long municipalityId,
            @Param("languageCode") LanguageCode languageCode
    );
}
