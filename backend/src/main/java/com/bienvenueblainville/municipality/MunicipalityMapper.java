package com.bienvenueblainville.municipality;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface MunicipalityMapper {
    List<Municipality> findAll();

    Optional<Municipality> findBySlug(@Param("slug") String slug);

    List<MunicipalitySector> findSectors(@Param("municipalityId") long municipalityId);
}
