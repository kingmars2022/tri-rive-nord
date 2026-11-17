package com.tririvenord.assistant;

import com.tririvenord.assistant.dto.AssistantAnswer;
import com.tririvenord.common.LanguageCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The property that four cities buy: one city's answer never reaches another
 * city's resident.
 *
 * <p>This is a test about a defect that has not happened, which is unusual
 * here -- most of this suite exists because something did. It is written
 * because the shape is familiar: the collection calendar printed the wrong bin
 * for weeks while every test passed, since no test compared it to anything
 * outside itself. Scoping retrieval and the cache by municipality is the same
 * kind of claim, and a claim with no test that could falsify it is not a
 * claim.
 *
 * <p>Two independent paths have to hold, and the second is the one easy to
 * miss. Retrieval can be perfectly scoped and the cache still leak: the key is
 * built from the question, and "ou va une boite a pizza ?" is the same string
 * in all four cities while the answer may differ in each.
 */
class CrossMunicipalityIsolationTest {
    private static final long BLAINVILLE = 1L;
    private static final long ROSEMERE = 4L;

    @Mock
    private SortingSearchMapper mapper;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> values;

    private SortingGuideRetriever retriever;
    private AnswerCache cache;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        retriever = new SortingGuideRetriever(mapper);
        when(redis.opsForValue()).thenReturn(values);
        cache = new AnswerCache(redis);
    }

    @Test
    @DisplayName("retrieval searches only the city that was asked about")
    void retrievalIsScopedToOneCity() {
        when(mapper.search(anyLong(), any(), any(), anyInt(), anyBoolean()))
                .thenReturn(List.of());

        retriever.retrieve("ou va une boite a pizza ?", LanguageCode.fr, ROSEMERE);

        // eq(ROSEMERE), not any(): passing the argument and then ignoring it in
        // the SQL would pass a looser assertion.
        verify(mapper).search(eq(ROSEMERE), eq(LanguageCode.fr), any(), anyInt(), eq(false));
    }

    @Test
    @DisplayName("the same question in two cities is two cache entries")
    void cacheKeysDifferPerCity() {
        AssistantAnswer answer = new AssistantAnswer(
                "Bac brun.", true, "template", List.of());

        cache.store(BLAINVILLE, "ou va une boite a pizza ?", LanguageCode.fr, "template", answer);
        cache.store(ROSEMERE, "ou va une boite a pizza ?", LanguageCode.fr, "template", answer);

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(values, org.mockito.Mockito.times(2))
                .set(keys.capture(), any(), any(Duration.class));

        assertThat(keys.getAllValues())
                .as("identical question, identical language, different city")
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("a lookup in one city cannot read the other city's entry")
    void cacheLookupIsScopedToOneCity() {
        cache.find(BLAINVILLE, "ou va une boite a pizza ?", LanguageCode.fr, "template");
        cache.find(ROSEMERE, "ou va une boite a pizza ?", LanguageCode.fr, "template");

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(values, org.mockito.Mockito.times(2)).get(keys.capture());

        assertThat(keys.getAllValues()).doesNotHaveDuplicates();
    }
}
