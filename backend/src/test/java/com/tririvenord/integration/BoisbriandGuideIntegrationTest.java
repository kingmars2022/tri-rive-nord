package com.tririvenord.integration;

import com.tririvenord.assistant.RetrievedItem;
import com.tririvenord.assistant.SortingGuideRetriever;
import com.tririvenord.common.LanguageCode;
import com.tririvenord.municipality.MunicipalityService;
import com.tririvenord.sorting.DestinationType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boisbriand's guide, asked what a resident asks, through the assistant's own
 * retriever.
 *
 * <p>Each expected destination is a line the city printed, cited beside it:
 * its 2026 collections page, or pages 3-5 of its 2025 calendar (see V28 for
 * why that edition). The questions are the ones where Boisbriand's answer is
 * the one a resident would not guess.
 *
 * <p>Run with: {@code mvn test -Pintegration-test}
 */
@Tag("integration")
@SpringBootTest
class BoisbriandGuideIntegrationTest {

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        IntegrationEnvironment.register(registry);
        registry.add("app.collection.calendar.enabled", () -> "false");
    }

    @Autowired
    private SortingGuideRetriever retriever;
    @Autowired
    private MunicipalityService municipalities;
    @Autowired
    private JdbcTemplate jdbc;

    private long boisbriand() {
        return municipalities.requireBySlug("boisbriand").id();
    }

    private void answers(String question, LanguageCode language, DestinationType expected) {
        List<RetrievedItem> found = retriever.retrieve(question, language, boisbriand());
        assertThat(found).as("no card at all for: %s", question).isNotEmpty();
        assertThat(found.getFirst().destinationType())
                .as("%s -> %s", question, found.getFirst().name())
                .isEqualTo(expected);
    }

    @Test
    void theBrownBinTakesWhatAResidentWouldNotGuess() {
        // 2025 calendar p.4: "la litiere, les excrements d'animaux domestiques
        // et le fumier de poule"; "les cendres (refroidies depuis plus de 72 h)".
        answers("Ou va la litiere du chat ?", LanguageCode.fr, DestinationType.organic);
        answers("Where does dog poop go?", LanguageCode.en, DestinationType.organic);
        answers("Can I put fireplace ashes in the brown bin?", LanguageCode.en, DestinationType.organic);
    }

    @Test
    void andRefusesWhatAResidentWouldPutInIt() {
        // p.4: "Evitez de deposer des plantes envahissantes dans le bac brun;
        // jetez-les plutot aux ordures"; papers "cires, plastifies, lamines ...
        // a la poubelle". 2026 page: no plastic bag, "meme ... compostable".
        answers("Where do invasive plants go?", LanguageCode.en, DestinationType.garbage);
        answers("Ou jeter du papier cire ?", LanguageCode.fr, DestinationType.garbage);
        answers("Can I put compostable bags in the brown bin?", LanguageCode.en, DestinationType.garbage);
    }

    @Test
    void theBlueBinIsContainersPackagingAndPrintedPaper_withItsExceptions() {
        // p.3: "Emballage de mets a emporter"; exceptions "Aerosols et
        // emballages de protection en polystyrene: les apporter a l'ecocentre".
        answers("Where do take-out containers go?", LanguageCode.en, DestinationType.recycling);
        answers("Where do aerosol spray cans go?", LanguageCode.en, DestinationType.ecocentre);
        answers("Ou va le styromousse de protection ?", LanguageCode.fr, DestinationType.ecocentre);
        // "Vetement et tissu" is on its "ne pas mettre dans le bac" list; p.5
        // sends clothing to reuse.
        answers("Where do old clothes go?", LanguageCode.en, DestinationType.special);
    }

    @Test
    void theEcocentreCasesInEveryLanguage() {
        // 2026 page: electronics, tires, hazardous waste "ne sont pas ramasses.
        // Vous pouvez en disposer a l'ecocentre."
        answers("Where do old tires go?", LanguageCode.en, DestinationType.ecocentre);
        answers("Ou apporter de la peinture ?", LanguageCode.fr, DestinationType.ecocentre);
        answers("废电池怎么处理？", LanguageCode.zh, DestinationType.ecocentre);
    }

    /** As for Sainte-Therese: an entry no search for its own name can reach is not in the guide. */
    @Test
    void everyEntryIsTheTopAnswerForItsOwnName() {
        List<Map<String, Object>> names = jdbc.queryForList("""
                select t.item_id, t.language_code, t.name
                from sorting_item_translation t join sorting_item i on i.id = t.item_id
                where i.municipality_id = ?
                """, boisbriand());
        assertThat(names).hasSize(39 * 3);

        List<String> unreachable = new ArrayList<>();
        for (Map<String, Object> row : names) {
            long itemId = ((Number) row.get("item_id")).longValue();
            LanguageCode language = LanguageCode.valueOf((String) row.get("language_code"));
            String name = (String) row.get("name");
            List<RetrievedItem> found = retriever.retrieve(name, language, boisbriand());
            if (found.isEmpty() || found.getFirst().itemId() != itemId) {
                unreachable.add(language + " \"" + name + "\" -> "
                        + (found.isEmpty() ? "nothing" : found.getFirst().name()));
            }
        }
        assertThat(unreachable).isEmpty();
    }

    @Test
    void theCityIsMarkedGuideLoaded_andEveryCardCitesACityPage() {
        assertThat(municipalities.requireBySlug("boisbriand").guideVerified()).isTrue();
        assertThat(jdbc.queryForList("select source_url from sorting_item where municipality_id = ?",
                String.class, boisbriand()))
                .hasSize(39)
                .allSatisfy(url -> assertThat(url).satisfiesAnyOf(
                        u -> assertThat(u).startsWith("https://www.ville.boisbriand.qc.ca/"),
                        u -> assertThat(u).isEqualTo("https://www.sainte-therese.ca/services/services-aux-citoyens/ecocentre")));
    }
}
