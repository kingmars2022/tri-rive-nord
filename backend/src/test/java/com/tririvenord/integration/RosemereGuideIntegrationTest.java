package com.tririvenord.integration;

import com.tririvenord.assistant.RetrievedItem;
import com.tririvenord.assistant.SortingGuideRetriever;
import com.tririvenord.collection.BinColor;
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
 * Rosemere's guide, asked what a resident asks, through the assistant's own
 * retriever.
 *
 * <p>Each expected destination is a line of page 30 of the town's 2026
 * municipal calendar (or its month grids, for the pickups), cited beside it.
 * The questions are the ones where Rosemere's answer differs from a
 * neighbour's - soiled litter is grey-bin here and brown-bin in Boisbriand -
 * or is one a resident would not guess.
 *
 * <p>Run with: {@code mvn test -Pintegration-test}
 */
@Tag("integration")
@SpringBootTest
class RosemereGuideIntegrationTest {

    private static final int ITEMS = 32;

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

    private long rosemere() {
        return municipalities.requireBySlug("rosemere").id();
    }

    private RetrievedItem answers(String question, LanguageCode language, DestinationType expected) {
        List<RetrievedItem> found = retriever.retrieve(question, language, rosemere());
        assertThat(found).as("no card at all for: %s", question).isNotEmpty();
        assertThat(found.getFirst().destinationType())
                .as("%s -> %s", question, found.getFirst().name())
                .isEqualTo(expected);
        return found.getFirst();
    }

    @Test
    void theGreyBinTakesWhatAResidentWouldNotGuess() {
        // p.30, BAC GRIS: "Ampoules electriques", "Boyaux d'arrosage",
        // "Charpie de secheuse", "Litiere souillee", "Poterie".
        RetrievedItem bulbs = answers("Where do lightbulbs go?", LanguageCode.en, DestinationType.garbage);
        assertThat(bulbs.binColor()).isEqualTo(BinColor.grey);
        answers("Ou va le boyau d'arrosage ?", LanguageCode.fr, DestinationType.garbage);
        answers("Ou va la charpie de la secheuse ?", LanguageCode.fr, DestinationType.garbage);
        // Not the brown bin, as next door in Boisbriand: the brown list
        // refuses "Litiere et excrements d'animaux".
        answers("Where does cat litter go?", LanguageCode.en, DestinationType.garbage);
        answers("Where does broken pottery go?", LanguageCode.en, DestinationType.garbage);
    }

    @Test
    void theBrownBinTakesAshesTissuesAndSoiledCardboard() {
        // p.30, BAC BRUN: "Cendres froides (foyer)", "Fibres sanitaires
        // (papier essuie-mains, mouchoirs, etc.)", "boites de pizza".
        answers("Can I put fireplace ashes in the brown bin?", LanguageCode.en, DestinationType.organic);
        answers("Ou vont les mouchoirs ?", LanguageCode.fr, DestinationType.organic);
        answers("Where does a greasy pizza box go?", LanguageCode.en, DestinationType.organic);
        answers("鸡蛋壳放哪里？", LanguageCode.zh, DestinationType.organic);
    }

    @Test
    void theBlueBinTakesContainersPaperAndMetal() {
        // p.30, BAC BLEU: "Plastique (tous les contenants ...)", "Metal
        // (boites de conserve, etc.)", "Carton".
        answers("Where do food cans go?", LanguageCode.en, DestinationType.recycling);
        answers("Ou va le carton ?", LanguageCode.fr, DestinationType.recycling);
        answers("塑料容器放哪里？", LanguageCode.zh, DestinationType.recycling);
    }

    @Test
    void thePickupsCarryThe2026Mondays() {
        // Month grids: large items one Monday a month, branches April to November.
        RetrievedItem large = answers("When is the large items pickup?", LanguageCode.en, DestinationType.bulky);
        assertThat(jdbc.queryForObject(
                "select availability from sorting_item_translation where item_id = ? and language_code = 'en'",
                String.class, large.itemId())).contains("14 December");
        answers("Ou vont les branches ?", LanguageCode.fr, DestinationType.special);
    }

    /** The calendar refuses tires and batteries everywhere and never says where they go: no card, so a refusal. */
    @Test
    void whatTheCalendarNeverPlacesIsRefusedRatherThanGuessed() {
        assertThat(retriever.retrieve("Where do old tires go?", LanguageCode.en, rosemere())).isEmpty();
        assertThat(retriever.retrieve("Ou apporter des piles usagees ?", LanguageCode.fr, rosemere())).isEmpty();
    }

    /** As for the other towns: an entry no search for its own name can reach is not in the guide. */
    @Test
    void everyEntryIsTheTopAnswerForItsOwnName() {
        List<Map<String, Object>> names = jdbc.queryForList("""
                select t.item_id, t.language_code, t.name
                from sorting_item_translation t join sorting_item i on i.id = t.item_id
                where i.municipality_id = ?
                """, rosemere());
        assertThat(names).hasSize(ITEMS * 3);

        List<String> unreachable = new ArrayList<>();
        for (Map<String, Object> row : names) {
            long itemId = ((Number) row.get("item_id")).longValue();
            LanguageCode language = LanguageCode.valueOf((String) row.get("language_code"));
            String name = (String) row.get("name");
            List<RetrievedItem> found = retriever.retrieve(name, language, rosemere());
            if (found.isEmpty() || found.getFirst().itemId() != itemId) {
                unreachable.add(language + " \"" + name + "\" -> "
                        + (found.isEmpty() ? "nothing" : found.getFirst().name()));
            }
        }
        assertThat(unreachable).isEmpty();
    }

    @Test
    void theTownIsMarkedGuideLoaded_andEveryCardCitesTheTownsSite() {
        assertThat(municipalities.requireBySlug("rosemere").guideVerified()).isTrue();
        assertThat(jdbc.queryForList("select source_url from sorting_item where municipality_id = ?",
                String.class, rosemere()))
                .hasSize(ITEMS)
                .allSatisfy(url -> assertThat(url).isEqualTo("https://www.ville.rosemere.qc.ca/"));
    }
}
