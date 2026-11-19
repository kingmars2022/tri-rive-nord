package com.tririvenord.assistant;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Relevance ranking for one city's sorting guide, in one language.
 *
 * <p>This used to be MySQL's natural-language full-text search; the database
 * is now PostgreSQL, whose full-text ranking and tokenizers behave
 * differently and which has no bigram parser for Chinese. Rather than tune
 * the assistant again against a different engine, the ranking MySQL's InnoDB
 * documents is reproduced here, where it can be unit-tested:
 *
 * <pre>
 *   score(term, document) = TF * IDF * IDF
 *   TF  = how many times the term occurs in the document
 *   IDF = log10(documents / documents containing the term)
 * </pre>
 *
 * summed over the distinct terms of the query. A term found in every document
 * scores nothing, exactly as in InnoDB.
 *
 * <p>An entry's score is the score of its name and instruction (one document)
 * plus the scores of each of its curated keywords (one document per keyword,
 * against the corpus of keywords), as the SQL used to add them.
 *
 * <p>One deliberate difference: InnoDB counted documents over the whole
 * table, every city and language together, so loading one city's guide
 * reordered another city's answers (V24 and V29 are the scars). Here the
 * corpus is the one city and language being searched.
 *
 * <p>Tokens: for French and English, words of at least three letters,
 * without InnoDB's stopwords (MySQL's {@code innodb_ft_min_token_size} and
 * default stopword list). For Chinese, overlapping two-character sequences,
 * as MySQL's ngram parser produced; see V7 for why that is not used for the
 * other two languages. Accents and case are folded away in both, as the
 * column collation did.
 */
final class SortingSearchIndex {
    private static final Pattern NOT_WORD = Pattern.compile("[^\\p{L}\\p{N}_]+");
    private static final Pattern DIACRITIC = Pattern.compile("\\p{M}+");
    private static final int MIN_WORD_LENGTH = 3;
    private static final int MAX_WORD_LENGTH = 84;
    private static final int NGRAM = 2;

    /** InnoDB's default full-text stopword list (INNODB_FT_DEFAULT_STOPWORD). */
    private static final Set<String> INNODB_STOPWORDS = Set.of(
            "a", "about", "an", "are", "as", "at", "be", "by", "com", "de", "en", "for",
            "from", "how", "i", "in", "is", "it", "la", "of", "on", "or", "that", "the",
            "this", "to", "was", "what", "when", "where", "who", "will", "with", "und",
            "www");

    private SortingSearchIndex() {
    }

    /** An entry's searchable text: its name and instruction. */
    record Document(long itemId, String name, String instruction) {
    }

    /** One curated keyword of an entry. */
    record Keyword(long itemId, String keyword) {
    }

    /** An entry and its score; entries scoring zero are not returned. */
    record Scored(long itemId, double score) {
    }

    /**
     * @return entries with a positive score, best first, at most {@code limit};
     *         equal scores in item order, so the same question always gets the
     *         same answer
     */
    static List<Scored> rank(List<Document> documents, List<Keyword> keywords,
                             String query, boolean ngram, int limit) {
        Set<String> terms = new LinkedHashSet<>(tokens(query, ngram));
        if (terms.isEmpty() || documents.isEmpty()) {
            return List.of();
        }

        List<List<String>> documentTokens = documents.stream()
                .map(d -> tokens(join(d.name(), d.instruction()), ngram))
                .toList();
        List<List<String>> keywordTokens = keywords.stream()
                .map(k -> tokens(k.keyword(), ngram))
                .toList();
        Map<String, Double> documentIdf = idf(terms, documentTokens);
        Map<String, Double> keywordIdf = idf(terms, keywordTokens);

        Map<Long, Double> keywordScores = new HashMap<>();
        for (int i = 0; i < keywords.size(); i++) {
            double score = score(terms, keywordTokens.get(i), keywordIdf);
            if (score > 0) {
                keywordScores.merge(keywords.get(i).itemId(), score, Double::sum);
            }
        }

        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            long itemId = documents.get(i).itemId();
            double score = score(terms, documentTokens.get(i), documentIdf)
                    + keywordScores.getOrDefault(itemId, 0.0);
            if (score > 0) {
                scored.add(new Scored(itemId, score));
            }
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed()
                .thenComparingLong(Scored::itemId));
        return scored.size() > limit ? List.copyOf(scored.subList(0, limit)) : List.copyOf(scored);
    }

    private static Map<String, Double> idf(Set<String> terms, List<List<String>> corpus) {
        Map<String, Double> idf = new HashMap<>();
        for (String term : terms) {
            long containing = corpus.stream().filter(tokens -> tokens.contains(term)).count();
            idf.put(term, containing == 0 ? 0.0 : Math.log10((double) corpus.size() / containing));
        }
        return idf;
    }

    private static double score(Set<String> terms, List<String> tokens, Map<String, Double> idf) {
        double score = 0;
        for (String term : terms) {
            double weight = idf.get(term);
            if (weight == 0) {
                continue;
            }
            long tf = tokens.stream().filter(term::equals).count();
            score += tf * weight * weight;
        }
        return score;
    }

    static List<String> tokens(String text, boolean ngram) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String word : NOT_WORD.split(fold(text))) {
            if (word.isEmpty()) {
                continue;
            }
            if (ngram) {
                for (int i = 0; i + NGRAM <= word.length(); i++) {
                    tokens.add(word.substring(i, i + NGRAM));
                }
            } else if (word.length() >= MIN_WORD_LENGTH && word.length() <= MAX_WORD_LENGTH
                    && !INNODB_STOPWORDS.contains(word)) {
                tokens.add(word);
            }
        }
        return tokens;
    }

    /** Lowercase without accents: what utf8mb4_0900_ai_ci compared on. */
    static String fold(String text) {
        String decomposed = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return DIACRITIC.matcher(decomposed).replaceAll("");
    }

    private static String join(String name, String instruction) {
        return instruction == null ? name : name + " " + instruction;
    }
}
