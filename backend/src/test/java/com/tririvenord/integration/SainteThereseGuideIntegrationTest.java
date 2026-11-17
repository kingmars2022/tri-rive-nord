package com.tririvenord.integration;

import com.tririvenord.assistant.RetrievedItem;
import com.tririvenord.assistant.SortingGuideRetriever;
import com.tririvenord.common.LanguageCode;
import com.tririvenord.municipality.Municipality;
import com.tririvenord.municipality.MunicipalityService;
import com.tririvenord.sorting.DestinationType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sainte-Therese's guide, asked the questions a resident asks, with the
 * answers taken from the city's regulation rather than from the rows V23
 * wrote.
 *
 * <p>Every expected destination below is a line in Reglement 1373 N.S.
 * (annexes A to E, archived under
 * data/municipal-sources/sainte-therese/2026-11-17/), cited next to it. The
 * questions are the ones where this city's answer is the surprising one -
 * where it contradicts Blainville, or contradicts the guess a resident would
 * make - because a guide that is only right where everyone already knows the
 * answer has not been tested.
 *
 * <p>They go through the same retriever the assistant uses, so a question
 * that returns the wrong card, or another city's card, fails here exactly as
 * it would fail a resident.
 *
 * <p>Run with: {@code mvn test -Pintegration-test}
 */
@Tag("integration")
@SpringBootTest
class SainteThereseGuideIntegrationTest {

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

    private long sainteTherese() {
        return municipalities.requireBySlug("sainte-therese").id();
    }

    private long blainville() {
        return municipalities.requireBySlug("blainville").id();
    }

    private RetrievedItem topAnswer(String question, LanguageCode language, long city) {
        List<RetrievedItem> found = retriever.retrieve(question, language, city);
        assertThat(found).as("no card at all for: %s", question).isNotEmpty();
        return found.getFirst();
    }

    private void answers(String question, LanguageCode language, DestinationType expected) {
        RetrievedItem top = topAnswer(question, language, sainteTherese());
        assertThat(top.destinationType())
                .as("%s -> %s", question, top.name())
                .isEqualTo(expected);
        assertThat(top.sourceUrl())
                .as("a Sainte-Therese answer cites Sainte-Therese")
                .startsWith("https://www.sainte-therese.ca/");
    }

    // ---- Where this city and Blainville disagree about the same item -----

    @Test
    void lightBulbs_goToTheEcocentre_notTheBlackBin() {
        // Annexe E, residus domestiques dangereux: "ampoules, fluocompactes,
        // tubes fluorescents".
        answers("Where do light bulbs go?", LanguageCode.en, DestinationType.ecocentre);

        assertThat(topAnswer("Where do light bulbs go?", LanguageCode.en, blainville()).destinationType())
                .as("Blainville's own guide still answers with its own rule")
                .isEqualTo(DestinationType.garbage);
    }

    @Test
    void aMirror_goesInTheBlackBin_notTheBulkyPickup() {
        // Annexe A: "Verre (ne provenant pas d'un contenant alimentaire),
        // vaisselle, miroirs, porcelaine et ustensiles".
        answers("Ou jeter un miroir casse ?", LanguageCode.fr, DestinationType.garbage);

        assertThat(topAnswer("Ou jeter un miroir casse ?", LanguageCode.fr, blainville()).destinationType())
                .isEqualTo(DestinationType.bulky);
    }

    // ---- Where a resident's guess is likely wrong ------------------------

    @Test
    void catLitter_goesInTheBrownBin() {
        // Annexe C, autres matieres compostables: "Litiere."
        answers("Ou va la litiere du chat ?", LanguageCode.fr, DestinationType.organic);
        answers("Where does cat litter go?", LanguageCode.en, DestinationType.organic);
    }

    @Test
    void foamFoodTrays_goInTheBlueBin_butProtectiveFoamDoesNot() {
        // Annexe B: "Emballages ou contenants alimentaires en polystyrene (PS)
        // [...] a l'exclusion de l'emballage de protection en PS".
        answers("Can I recycle a styrofoam meat tray?", LanguageCode.en, DestinationType.recycling);
        answers("Where does the protective styrofoam from a TV box go?", LanguageCode.en,
                DestinationType.ecocentre);
    }

    @Test
    void plasticBagsAndCoffeeCapsules_goInTheBlueBin() {
        // Annexe B: "Sacs et pellicules d'emballage en plastique"; "Capsules
        // (cafe, the) en PP (no 5), et en PS (no 6)"; "Capsules de cafe en
        // aluminium".
        answers("Where do plastic grocery bags go?", LanguageCode.en, DestinationType.recycling);
        answers("Ou vont les capsules de cafe ?", LanguageCode.fr, DestinationType.recycling);
    }

    @Test
    void compostableBags_areRefusedInTheBrownBin() {
        // Collections page: "Les sacs de plastique, meme biodegradables ou
        // compostables sont interdits"; annexe B excludes degradable plastics.
        answers("Can I put compostable bags in the brown bin?", LanguageCode.en, DestinationType.garbage);
    }

    @Test
    void aerosolCans_areNotRecycling() {
        // Annexe B excludes "contenants sous pression (contenants aerosol)";
        // annexe E takes "aerosols" as household hazardous waste.
        answers("Where do aerosol spray cans go?", LanguageCode.en, DestinationType.ecocentre);
    }

    @Test
    void photosAndCds_goInTheBlackBin() {
        // Annexe A: "Photos"; "Disques compacts, cassettes audio et cassettes
        // video".
        answers("Where do old photos go?", LanguageCode.en, DestinationType.garbage);
        answers("Ou jeter des disques compacts ?", LanguageCode.fr, DestinationType.garbage);
    }

    // ---- The everyday questions, in all three languages -------------------

    @Test
    void theEverydayQuestionsInEveryLanguage() {
        // Annexe C: "boites de pizza".
        answers("Ou va une boite a pizza sale ?", LanguageCode.fr, DestinationType.organic);
        // Annexe A: "Couches et produits hygieniques".
        answers("Where do diapers go?", LanguageCode.en, DestinationType.garbage);
        // Annexe E: "piles"; the Chinese question has no spaces, so this also
        // proves the ngram index holds the new rows.
        answers("废电池怎么处理？", LanguageCode.zh, DestinationType.ecocentre);
        // Annexe E: "Pneus avec ou sans jante".
        answers("Where do old tires go?", LanguageCode.en, DestinationType.ecocentre);
    }

    // ---- The guide as a whole ---------------------------------------------

    @Test
    void theCityIsMarkedGuideLoaded_andEveryEntryCitesTheCity() {
        Municipality city = municipalities.requireBySlug("sainte-therese");
        assertThat(city.guideVerified()).isTrue();
        assertThat(city.calendarVerified()).isTrue();

        List<String> sources = jdbc.queryForList(
                "select source_url from sorting_item where municipality_id = ?", String.class, sainteTherese());
        assertThat(sources).hasSize(62)
                .allSatisfy(url -> assertThat(url).startsWith("https://www.sainte-therese.ca/"));
    }

    /**
     * Every entry wins a search for its own name, in every language.
     *
     * <p>Not a nicety. Full-text scores here are summed over keywords and
     * weighted by word statistics across every city's rows, so an entry can
     * be quietly unreachable - a keyword repeated three times on a neighbour,
     * a plural the index does not stem - without any single row looking
     * wrong. The first draft of V23 had four: "Food can" and "Styrofoam food
     * containers" both returned Pet food, whose keywords said "food" three
     * times.
     */
    @Test
    void everyEntryIsTheTopAnswerForItsOwnName() {
        List<java.util.Map<String, Object>> names = jdbc.queryForList("""
                select t.item_id, t.language_code, t.name
                from sorting_item_translation t join sorting_item i on i.id = t.item_id
                where i.municipality_id = ?
                """, sainteTherese());
        assertThat(names).hasSize(62 * 3);

        List<String> unreachable = new java.util.ArrayList<>();
        for (java.util.Map<String, Object> row : names) {
            long itemId = ((Number) row.get("item_id")).longValue();
            LanguageCode language = LanguageCode.valueOf((String) row.get("language_code"));
            String name = (String) row.get("name");
            List<RetrievedItem> found = retriever.retrieve(name, language, sainteTherese());
            if (found.isEmpty() || found.getFirst().itemId() != itemId) {
                unreachable.add(language + " \"" + name + "\" -> "
                        + (found.isEmpty() ? "nothing" : found.getFirst().name()));
            }
        }
        assertThat(unreachable).isEmpty();
    }

    @Test
    void sharedKeysLetTheTwoCitiesBeCompared() {
        // The point of item_key: "which city disagrees about this" is a join,
        // not a reading exercise.
        List<String> disagreements = jdbc.queryForList("""
                select st.item_key
                from sorting_item st
                join sorting_item bl on bl.item_key = st.item_key and bl.municipality_id = ?
                where st.municipality_id = ? and st.destination_type <> bl.destination_type
                order by st.item_key
                """, String.class, blainville(), sainteTherese());

        assertThat(disagreements).contains("broken-glass-or-mirror", "light-bulb", "branches");
    }
}
