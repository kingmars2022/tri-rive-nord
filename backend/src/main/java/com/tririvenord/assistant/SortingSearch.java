package com.tririvenord.assistant;

import com.tririvenord.common.LanguageCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ranked search over one city's sorting guide, in one language.
 *
 * <p>Reads the city's entries and keywords and ranks them with
 * {@link SortingSearchIndex}. What used to be one MATCH ... AGAINST query on
 * MySQL; the resident's words never reach SQL at all now.
 */
@Component
public class SortingSearch {
    private final SortingSearchMapper mapper;

    public SortingSearch(SortingSearchMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * @param municipalityId which city's rules to search; see
     *                       {@link SortingSearchMapper} for why it is required
     * @param languageCode   which language's rows to search
     * @param query          the resident's words, already normalized
     * @param limit          hard cap on entries returned, so a broad query
     *                       cannot drag the whole guide into an LLM prompt
     * @param ngram          true to match on character pairs (Chinese), false
     *                       on words (French, English); see V7
     * @return entries with a positive score, best first
     */
    public List<RetrievedItem> search(long municipalityId, LanguageCode languageCode,
                                      String query, int limit, boolean ngram) {
        List<RetrievedItem> entries = mapper.findEntries(municipalityId, languageCode);
        if (entries.isEmpty()) {
            return List.of();
        }
        List<SortingSearchIndex.Document> documents = entries.stream()
                .map(e -> new SortingSearchIndex.Document(e.itemId(), e.name(), e.instruction()))
                .toList();
        List<SortingSearchIndex.Keyword> keywords = mapper.findKeywords(municipalityId, languageCode);

        Map<Long, RetrievedItem> byId = entries.stream()
                .collect(Collectors.toMap(RetrievedItem::itemId, Function.identity(), (a, b) -> a));
        return SortingSearchIndex.rank(documents, keywords, query, ngram, limit).stream()
                .map(s -> withScore(byId.get(s.itemId()), s.score()))
                .toList();
    }

    private static RetrievedItem withScore(RetrievedItem e, double score) {
        return new RetrievedItem(e.itemId(), e.languageCode(), e.name(), e.instruction(), e.location(),
                e.destinationType(), e.binColor(), e.sourceUrl(), score);
    }
}
