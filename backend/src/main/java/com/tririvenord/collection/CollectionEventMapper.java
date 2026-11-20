package com.tririvenord.collection;

import com.tririvenord.common.Sector;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Mapper
public interface CollectionEventMapper {
    /** Removes generated occurrences from {@code from} onward, leaving hand-entered ones. */
    void deleteGeneratedFrom(@Param("municipalityId") long municipalityId,
                             @Param("from") LocalDate from);

    List<CollectionEvent> findUpcoming(
            @Param("municipalityId") long municipalityId,
            @Param("sector") Sector sector,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    List<CollectionEvent> findAll(@Param("municipalityId") long municipalityId);

    List<CollectionEvent> findPage(@Param("municipalityId") long municipalityId,
                                   @Param("today") java.time.LocalDate today,
                                   @Param("offset") int offset,
                                   @Param("limit") int limit);

    long countAll(@Param("municipalityId") long municipalityId);

    /** All sectors, from a date forward, bounded. See CollectionService.forPlanning. */
    List<CollectionEvent> findFrom(
            @Param("municipalityId") long municipalityId,
            @Param("startDate") LocalDate startDate,
            @Param("limit") int limit
    );

    Optional<CollectionEvent> findById(@Param("id") Long id);

    void insert(Map<String, Object> params);

    /** Bulk insert for generated occurrences; see CollectionCalendarTopUp. */
    void insertGenerated(@Param("events") List<CollectionEvent> events);

    void update(
            @Param("id") Long id,
            @Param("collectionDate") LocalDate collectionDate,
            @Param("sector") Sector sector,
            @Param("collectionType") CollectionType collectionType,
            @Param("binColor") BinColor binColor,
            @Param("noteFr") String noteFr,
            @Param("noteEn") String noteEn,
            @Param("noteZh") String noteZh,
            @Param("sourceUrl") String sourceUrl
    );

    void delete(@Param("id") Long id);
}
