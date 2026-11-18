package com.tririvenord.notice;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Mapper
public interface SpecialNoticeMapper {
    List<SpecialNotice> findActive(@Param("municipalityId") long municipalityId, @Param("today") LocalDate today);

    List<SpecialNotice> findAll(@Param("municipalityId") long municipalityId);

    List<SpecialNotice> findRecent(@Param("municipalityId") long municipalityId, @Param("limit") int limit);

    Optional<SpecialNotice> findById(@Param("id") Long id);

    void insert(Map<String, Object> params);

    void update(SpecialNotice notice);

    void delete(@Param("id") Long id);
}
