package com.tririvenord.assistant;

import com.tririvenord.assistant.SortingSearchIndex.Document;
import com.tririvenord.assistant.SortingSearchIndex.Keyword;
import com.tririvenord.assistant.SortingSearchIndex.Scored;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The ranking that replaced MySQL's MATCH ... AGAINST, checked against the
 * rules it reproduces: TF * IDF * IDF summed over distinct terms, words of
 * three letters or more for French and English, character pairs for Chinese,
 * accents and case ignored.
 */
class SortingSearchIndexTest {
    private static final List<Document> GUIDE = List.of(
            new Document(1, "Boîte à pizza", "Dans le bac brun si elle est souillée."),
            new Document(2, "Carton", "Dans le bac bleu, aplati."),
            new Document(3, "Piles", "À l'écocentre."),
            new Document(4, "Peinture", "À l'écocentre, dans son contenant d'origine."));

    private static List<Scored> rank(String query, List<Keyword> keywords) {
        return SortingSearchIndex.rank(GUIDE, keywords, query, false, 5);
    }

    @Test
    void scoresTermFrequencyTimesIdfSquared() {
        // "piles" is in one document of four: IDF = log10(4/1).
        List<Scored> result = rank("piles", List.of());

        assertThat(result).extracting(Scored::itemId).containsExactly(3L);
        double idf = Math.log10(4.0);
        assertThat(result.getFirst().score()).isCloseTo(idf * idf, within(1e-9));
    }

    @Test
    void ignoresAccentsAndCase() {
        assertThat(rank("BOITE souillee", List.of())).extracting(Scored::itemId).containsExactly(1L);
        assertThat(rank("ecocentre", List.of())).extracting(Scored::itemId).containsExactly(3L, 4L);
    }

    @Test
    void aWordInEveryDocumentScoresNothing() {
        // "dans" is in three of four; "bac" in two. Neither is in all four, so
        // add one that is.
        List<Document> guide = List.of(
                new Document(1, "Bac brun", "bac"),
                new Document(2, "Bac bleu", "bac"));

        assertThat(SortingSearchIndex.rank(guide, List.of(), "bac", false, 5)).isEmpty();
    }

    @Test
    void shortWordsAndInnodbStopwordsAreNotTerms() {
        assertThat(SortingSearchIndex.tokens("Où va le carton of the box", false))
                .containsExactly("carton", "box");
    }

    @Test
    void keywordsAddToTheEntrysScore() {
        List<Keyword> keywords = List.of(
                new Keyword(2, "boîte de carton"),
                new Keyword(2, "boîte"),
                new Keyword(3, "batteries"));

        List<Scored> withKeywords = rank("boite", keywords);
        // Entry 1 has the word in its name; entry 2 only through keywords,
        // where it appears in two of three keyword rows.
        assertThat(withKeywords).extracting(Scored::itemId).containsExactlyInAnyOrder(1L, 2L);
        double keywordIdf = Math.log10(3.0 / 2.0);
        Scored carton = withKeywords.stream().filter(s -> s.itemId() == 2).findFirst().orElseThrow();
        assertThat(carton.score()).isCloseTo(2 * keywordIdf * keywordIdf, within(1e-9));
    }

    @Test
    void aQuestionTheGuideCannotAnswerScoresNothing() {
        assertThat(rank("réparer ma voiture", List.of())).isEmpty();
    }

    @Test
    void chineseIsMatchedOnCharacterPairs() {
        List<Document> guide = List.of(
                new Document(1, "废电池", "请送到生态中心。"),
                new Document(2, "纸箱", "放入蓝桶。"),
                new Document(3, "油漆", "请送到生态中心。"));

        List<Scored> result = SortingSearchIndex.rank(guide, List.of(), "废电池怎么处理？", true, 5);

        assertThat(result).extracting(Scored::itemId).containsExactly(1L);
        assertThat(SortingSearchIndex.tokens("废电池，蓝桶", true)).containsExactly("废电", "电池", "蓝桶");
    }

    @Test
    void equalScoresComeInItemOrderAndTheLimitHolds() {
        List<Document> guide = List.of(
                new Document(9, "Verre", "x"),
                new Document(5, "Verre", "y"),
                new Document(7, "Plastique", "z"));

        assertThat(SortingSearchIndex.rank(guide, List.of(), "verre", false, 5))
                .extracting(Scored::itemId).containsExactly(5L, 9L);
        assertThat(SortingSearchIndex.rank(guide, List.of(), "verre", false, 1))
                .extracting(Scored::itemId).containsExactly(5L);
    }
}
