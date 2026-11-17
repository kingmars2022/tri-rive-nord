package com.tririvenord.collection;

import com.tririvenord.collection.dto.CollectionEventRequest;
import com.tririvenord.common.Sector;
import com.tririvenord.municipality.MunicipalityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectionServiceTest {
    /** Any one city; these tests are about behaviour, not about which. */
    private static final long MUNI = 1L;

    @Mock
    private CollectionEventMapper mapper;

    @Mock
    private MunicipalityService municipalities;

    private CollectionService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new CollectionService(mapper, municipalities);
    }

    @Test
    void upcomingQueriesFromTodayThroughTheRequestedWindow() {
        when(mapper.findUpcoming(eq(MUNI), eq(Sector.of("north")), any(), any())).thenReturn(List.of());

        service.upcoming(MUNI, Sector.of("north"), 14);

        ArgumentCaptor<LocalDate> startCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> endCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(mapper).findUpcoming(eq(MUNI), eq(Sector.of("north")), startCaptor.capture(), endCaptor.capture());

        LocalDate today = LocalDate.now();
        assertThat(startCaptor.getValue()).isEqualTo(today);
        assertThat(endCaptor.getValue()).isEqualTo(today.plusDays(14));
    }

    @Test
    void createReadsTheGeneratedIdBackFromTheSharedParamsMap() {
        doAnswer(invocation -> {
            Map<String, Object> params = invocation.getArgument(0);
            // MySQL's JDBC driver hands back generated keys for BIGINT columns
            // as BigInteger, not Long — assert against that, not a Long literal.
            params.put("id", java.math.BigInteger.valueOf(3));
            return null;
        }).when(mapper).insert(any());

        CollectionEvent created = new CollectionEvent(
                3L,MUNI,  LocalDate.now(), Sector.ALL, CollectionType.organic, BinColor.brown,
                null, null, null, null);
        when(mapper.findById(3L)).thenReturn(Optional.of(created));

        CollectionEvent result = service.create(MUNI, new CollectionEventRequest(
                LocalDate.now(), Sector.ALL, CollectionType.organic, BinColor.brown, null, null, null, null));

        assertThat(result.id()).isEqualTo(3L);
    }

    @Test
    void deleteRejectsAnUnknownId() {
        when(mapper.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");

        verify(mapper, never()).delete(99L);
    }

    @Test
    void createRejectsASectorTheCityDoesNotHaveBeforeWritingAnything() {
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "This city has no sector 'north'"))
                .when(municipalities).requireSectorForWrite(MUNI, Sector.of("north"));

        assertThatThrownBy(() -> service.create(MUNI, new CollectionEventRequest(
                LocalDate.now(), Sector.of("north"), CollectionType.organic, BinColor.brown,
                null, null, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no sector 'north'");

        verify(mapper, never()).insert(any());
    }

    @Test
    void updateChecksTheSectorAgainstTheStoredRowsCity() {
        long otherCity = 7L;
        when(mapper.findById(5L)).thenReturn(Optional.of(new CollectionEvent(
                5L, otherCity, LocalDate.now(), Sector.of("a"), CollectionType.garbage, BinColor.black,
                null, null, null, null)));

        service.update(5L, new CollectionEventRequest(
                LocalDate.now(), Sector.of("c"), CollectionType.garbage, BinColor.black, null, null, null, null));

        verify(municipalities).requireSectorForWrite(otherCity, Sector.of("c"));
    }
}
