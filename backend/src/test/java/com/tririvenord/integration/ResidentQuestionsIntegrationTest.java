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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Questions worded the way residents type them - lowercase, no accents,
 * plurals, brand names, slang, questions the guide cannot answer - asked of
 * every town's guide through the assistant's own retriever.
 *
 * <p>Each expected destination is the one that town's own guide gives; null
 * means the guide has nothing for it and the assistant must refuse rather
 * than guess. Written from a simulation of residents on 2026-11-20 (see
 * docs/verification/resident-simulation.md), which found eight wrong
 * answers before V34 and the search changes that came with it, including
 * "old paint cans" sent to Rosemere's blue bin and "What time does the
 * mayor's office open?" answered with Christmas trees.
 *
 * <p>Run with: {@code mvn test -Pintegration-test}
 */
@Tag("integration")
@SpringBootTest
class ResidentQuestionsIntegrationTest {
    private static final DestinationType REFUSE = null;

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        IntegrationEnvironment.register(registry);
        registry.add("app.collection.calendar.enabled", () -> "false");
    }

    @Autowired
    private SortingGuideRetriever retriever;
    @Autowired
    private MunicipalityService municipalities;

    private record Case(String town, LanguageCode language, String question, DestinationType expected) {
    }

    private static Case ask(String town, LanguageCode language, String question, DestinationType expected) {
        return new Case(town, language, question, expected);
    }

    private static final List<Case> CASES = List.of(
            ask("blainville", LanguageCode.fr, "ou je met mes pelures de patate", DestinationType.organic),
            ask("blainville", LanguageCode.fr, "Les os de poulet ça va où?", DestinationType.organic),
            ask("blainville", LanguageCode.fr, "boite de pizza graisseuse", DestinationType.organic),
            ask("blainville", LanguageCode.fr, "Je peux mettre une bouteille de vin dans le bac bleu?", DestinationType.recycling),
            ask("blainville", LanguageCode.fr, "conserve de soupe vide", DestinationType.recycling),
            ask("blainville", LanguageCode.fr, "Où jeter une ampoule brûlée ?", DestinationType.garbage),
            ask("blainville", LanguageCode.fr, "vieux pot de peinture", DestinationType.ecocentre),
            ask("blainville", LanguageCode.fr, "Que faire de mon sapin de Noël après les fêtes?", DestinationType.special),
            ask("blainville", LanguageCode.fr, "Comment me débarrasser d'un vieux divan?", DestinationType.bulky),
            ask("blainville", LanguageCode.en, "where do coffee grounds go", DestinationType.organic),
            ask("blainville", LanguageCode.en, "Can I recycle a chip bag?", DestinationType.recycling),
            ask("blainville", LanguageCode.en, "broken plates", DestinationType.garbage),
            ask("blainville", LanguageCode.en, "dead batteries", DestinationType.ecocentre),
            ask("blainville", LanguageCode.en, "egg carton", DestinationType.recycling),
            ask("blainville", LanguageCode.en, "I have a broken mirror, which bin?", DestinationType.bulky),
            ask("blainville", LanguageCode.en, "what about kleenex", DestinationType.organic),
            ask("blainville", LanguageCode.en, "Where do I put grass clippings?", DestinationType.organic),
            ask("blainville", LanguageCode.en, "old water heater", DestinationType.bulky),
            ask("blainville", LanguageCode.zh, "鸡骨头扔哪个桶", DestinationType.organic),
            ask("blainville", LanguageCode.zh, "旧电池怎么处理", DestinationType.ecocentre),
            ask("blainville", LanguageCode.zh, "披萨盒放哪里？", DestinationType.organic),
            ask("blainville", LanguageCode.zh, "玻璃酒瓶", DestinationType.recycling),
            ask("blainville", LanguageCode.en, "What time does the mayor's office open?", REFUSE),
            ask("blainville", LanguageCode.fr, "Combien coûte un permis de construction?", REFUSE),
            ask("sainte-therese", LanguageCode.fr, "ou vont les piles usagées", DestinationType.ecocentre),
            ask("sainte-therese", LanguageCode.fr, "capsules de café Nespresso", DestinationType.recycling),
            ask("sainte-therese", LanguageCode.fr, "sac de plastique d'épicerie", DestinationType.recycling),
            ask("sainte-therese", LanguageCode.fr, "litière du chat", DestinationType.organic),
            ask("sainte-therese", LanguageCode.fr, "les cheveux après une coupe", DestinationType.organic),
            ask("sainte-therese", LanguageCode.fr, "bonbonne de propane vide", DestinationType.ecocentre),
            ask("sainte-therese", LanguageCode.fr, "Où va le styromousse des plats pour emporter?", DestinationType.recycling),
            ask("sainte-therese", LanguageCode.fr, "vieux pneus d'hiver", DestinationType.ecocentre),
            ask("sainte-therese", LanguageCode.en, "where do I throw diapers", DestinationType.garbage),
            ask("sainte-therese", LanguageCode.en, "old laptop", DestinationType.ecocentre),
            ask("sainte-therese", LanguageCode.en, "aerosol can of hairspray", DestinationType.ecocentre),
            ask("sainte-therese", LanguageCode.en, "milk carton", DestinationType.recycling),
            ask("sainte-therese", LanguageCode.en, "candle wax", DestinationType.garbage),
            ask("sainte-therese", LanguageCode.en, "dryer lint", DestinationType.garbage),
            ask("sainte-therese", LanguageCode.en, "used frying oil", DestinationType.ecocentre),
            ask("sainte-therese", LanguageCode.en, "my old fridge", DestinationType.special),
            ask("sainte-therese", LanguageCode.zh, "猫砂放哪里", DestinationType.organic),
            ask("sainte-therese", LanguageCode.zh, "电池", DestinationType.ecocentre),
            ask("sainte-therese", LanguageCode.zh, "塑料袋可以回收吗", DestinationType.recycling),
            ask("sainte-therese", LanguageCode.zh, "旧轮胎", DestinationType.ecocentre),
            ask("sainte-therese", LanguageCode.en, "Is the library open on Sunday?", REFUSE),
            ask("boisbriand", LanguageCode.fr, "excréments de chien", DestinationType.organic),
            ask("boisbriand", LanguageCode.fr, "cendres du foyer", DestinationType.organic),
            ask("boisbriand", LanguageCode.fr, "sacs compostables", DestinationType.garbage),
            ask("boisbriand", LanguageCode.fr, "vieux chaudrons", DestinationType.ecocentre),
            ask("boisbriand", LanguageCode.fr, "vêtements usagés", DestinationType.special),
            ask("boisbriand", LanguageCode.fr, "boyau d'arrosage percé", DestinationType.garbage),
            ask("boisbriand", LanguageCode.fr, "contenant de mets pour emporter", DestinationType.recycling),
            ask("boisbriand", LanguageCode.en, "where does a tree stump go", DestinationType.ecocentre),
            ask("boisbriand", LanguageCode.en, "old TV", DestinationType.ecocentre),
            ask("boisbriand", LanguageCode.en, "spray paint can", DestinationType.ecocentre),
            ask("boisbriand", LanguageCode.en, "newspapers", DestinationType.recycling),
            ask("boisbriand", LanguageCode.en, "aluminum foil", DestinationType.recycling),
            ask("boisbriand", LanguageCode.en, "invasive plants like buckthorn", DestinationType.garbage),
            ask("boisbriand", LanguageCode.en, "peanut shells", DestinationType.organic),
            ask("boisbriand", LanguageCode.zh, "旧衣服", DestinationType.special),
            ask("boisbriand", LanguageCode.zh, "油漆", DestinationType.ecocentre),
            ask("boisbriand", LanguageCode.zh, "狗屎", DestinationType.organic),
            ask("boisbriand", LanguageCode.en, "How do I pay my water bill?", REFUSE),
            ask("rosemere", LanguageCode.fr, "litière souillée du chat", DestinationType.garbage),
            ask("rosemere", LanguageCode.fr, "ampoule électrique grillée", DestinationType.garbage),
            ask("rosemere", LanguageCode.fr, "cendres froides de foyer", DestinationType.organic),
            ask("rosemere", LanguageCode.fr, "pot de yogourt en plastique", DestinationType.recycling),
            ask("rosemere", LanguageCode.fr, "boîte de conserve", DestinationType.recycling),
            ask("rosemere", LanguageCode.fr, "papier essuie-tout", DestinationType.organic),
            ask("rosemere", LanguageCode.fr, "couches de bébé", DestinationType.garbage),
            ask("rosemere", LanguageCode.fr, "vieux matelas", DestinationType.bulky),
            ask("rosemere", LanguageCode.fr, "branches coupées", DestinationType.special),
            ask("rosemere", LanguageCode.en, "where do I put lightbulbs", DestinationType.garbage),
            ask("rosemere", LanguageCode.en, "garden hose", DestinationType.garbage),
            ask("rosemere", LanguageCode.en, "pizza box", DestinationType.organic),
            ask("rosemere", LanguageCode.en, "glass jar", DestinationType.recycling),
            ask("rosemere", LanguageCode.en, "cardboard box", DestinationType.recycling),
            ask("rosemere", LanguageCode.en, "ice cream container", DestinationType.garbage),
            ask("rosemere", LanguageCode.en, "leaves and grass", DestinationType.organic),
            ask("rosemere", LanguageCode.en, "old couch", DestinationType.bulky),
            ask("rosemere", LanguageCode.en, "car battery", REFUSE),
            ask("rosemere", LanguageCode.en, "old paint cans", DestinationType.special),
            ask("rosemere", LanguageCode.en, "styrofoam", DestinationType.special),
            ask("rosemere", LanguageCode.zh, "灯泡", DestinationType.garbage),
            ask("rosemere", LanguageCode.zh, "猫砂", DestinationType.garbage),
            ask("rosemere", LanguageCode.zh, "纸箱", DestinationType.recycling),
            ask("rosemere", LanguageCode.zh, "旧沙发", DestinationType.bulky),
            ask("rosemere", LanguageCode.zh, "电池放哪里", REFUSE)
    );

    @Test
    void everyTownAnswersItsResidentsAsItsGuideDoes() {
        List<String> wrong = new ArrayList<>();
        for (Case c : CASES) {
            long town = municipalities.requireBySlug(c.town()).id();
            List<RetrievedItem> found = retriever.retrieve(c.question(), c.language(), town);
            DestinationType got = found.isEmpty() ? REFUSE : found.getFirst().destinationType();
            if (got != c.expected()) {
                wrong.add(c.town() + " [" + c.language() + "] \"" + c.question() + "\": expected "
                        + (c.expected() == null ? "a refusal" : c.expected()) + ", got "
                        + (got == null ? "a refusal" : got + " (" + found.getFirst().name() + ")"));
            }
        }
        assertThat(wrong).as("%d questions", CASES.size()).isEmpty();
    }
}
