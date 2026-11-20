package com.tririvenord.photo;

import com.tririvenord.assistant.AnswerComposer;
import com.tririvenord.assistant.AssistantQuestion;
import com.tririvenord.assistant.GuideCity;
import com.tririvenord.assistant.RetrievedItem;
import com.tririvenord.assistant.SortingGuideRetriever;
import com.tririvenord.assistant.dto.AssistantSource;
import com.tririvenord.common.LanguageCode;
import com.tririvenord.insights.QueryEventPublisher;
import com.tririvenord.photo.dto.PhotoAnswer;
import com.tririvenord.municipality.MunicipalityService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Photograph in, sorting rule out — with the rule coming from the municipal
 * guide rather than from the model.
 *
 * <p>The division of labour is the whole design:
 *
 * <pre>
 *   photo ──▶ vision model ──▶ "boîte à pizza"   (names the object, nothing else)
 *                                    │
 *                    the same retrieval a typed question uses
 *                                    ▼
 *                    guide entry ──▶ bin, instruction, source
 * </pre>
 *
 * <p>A vision model asked "which bin?" would answer from whatever it absorbed
 * about recycling in general, and Blainville's rules are not general — soiled
 * cardboard goes in the brown bin here and the black bin in plenty of other
 * municipalities. Splitting identification from lookup means the photo path
 * inherits the text assistant's guarantees for free, including the refusal:
 * an object the guide does not cover produces "I don't know", not a guess.
 */
@Service
public class PhotoSortingService {
    private static final Map<LanguageCode, String> UNIDENTIFIED = Map.of(
            LanguageCode.fr, "Je n'arrive pas à identifier cet objet sur la photo. "
                    + "Essayez une photo plus nette, ou décrivez-le avec des mots.",
            LanguageCode.en, "I can't make out what that is. Try a sharper photo, "
                    + "or describe it in words instead.",
            LanguageCode.zh, "看不清照片里是什么。请拍得清楚一些，或者直接用文字描述。"
    );

    private static final Map<LanguageCode, String> NOT_IN_GUIDE = Map.of(
            LanguageCode.fr, "J'ai reconnu « %s », mais cette matière n'est pas dans le guide de tri "
                    + "de Blainville. Consultez blainville.ca pour en avoir le cœur net.",
            LanguageCode.en, "I recognised \"%s\", but that material is not in the Blainville sorting "
                    + "guide. Check blainville.ca to be sure.",
            LanguageCode.zh, "识别为「%s」，但 Blainville 分类指南里没有收录这种物品。请查阅 blainville.ca 确认。"
    );

    private final PhotoStorage storage;
    private final PhotoIdentifier identifier;
    private final SortingGuideRetriever retriever;
    private final AnswerComposer composer;
    private final QueryEventPublisher queries;
    private final PhotoProperties properties;
    private final MunicipalityService municipalities;

    public PhotoSortingService(
            PhotoStorage storage,
            PhotoIdentifier identifier,
            SortingGuideRetriever retriever,
            AnswerComposer composer,
            QueryEventPublisher queries,
            PhotoProperties properties,
            MunicipalityService municipalities
    ) {
        this.storage = storage;
        this.identifier = identifier;
        this.retriever = retriever;
        this.composer = composer;
        this.queries = queries;
        this.properties = properties;
        this.municipalities = municipalities;
    }

    /**
     * @param municipalityId the city whose guide decides the bin. The model
     *                       only names the object; which bin that object goes
     *                       in is local, so the lookup is scoped exactly as
     *                       the typed assistant's is.
     */
    public Optional<PhotoAnswer> identify(String photoId, LanguageCode language, long municipalityId) {
        // The processed copy, never the original: the original still carries
        // the GPS coordinates of wherever the resident was standing, and there
        // is no reason to send those to a third party either.
        Optional<byte[]> photo = storage.awaitProcessed(
                photoId, Duration.ofSeconds(properties.processingTimeout()));
        if (photo.isEmpty()) {
            // Two very different situations, which used to collapse into one
            // 404 that said the photo did not exist. If the original is there,
            // the upload worked and the Lambda simply has not caught up (or
            // failed) — telling a resident "not found" for a photo they just
            // watched upload is the kind of wrong answer that makes people
            // retake the picture pointlessly.
            if (storage.readOriginal(photoId).isPresent()) {
                throw new PhotoNotReadyException();
            }
            return Optional.empty();
        }

        Optional<MaterialIdentification> identified = identifier.identify(photo.get(), language);
        if (identified.isEmpty()) {
            return Optional.of(new PhotoAnswer(
                    null, false, UNIDENTIFIED.get(language), "vision", List.of()));
        }

        MaterialIdentification material = identified.get();
        List<RetrievedItem> context = retriever.retrieve(material.searchQuery(), language, municipalityId);

        // Same stream as a typed question, tagged "photo". The same material
        // arrives worded very differently through the two paths, which is
        // exactly what makes the comparison worth having.
        queries.record("photo", language, material.material(), material.searchQuery(),
                !context.isEmpty(),
                context.isEmpty() ? 0d : context.get(0).score(),
                context.stream().map(RetrievedItem::name).toList());

        if (context.isEmpty()) {
            // Recognised, but not in the guide. Saying what it thought it saw
            // matters here: it tells the resident whether the miss was the
            // model's fault or the guide's, which is the difference between
            // retaking the photo and phoning the city.
            return Optional.of(new PhotoAnswer(
                    material.material(), false,
                    NOT_IN_GUIDE.get(language).formatted(material.material()),
                    "vision", List.of()));
        }

        String answer = composer.composeWithMetadata(
                new AssistantQuestion(material.searchQuery(), language,
                        GuideCity.of(municipalities.requireById(municipalityId))), context).text();

        return Optional.of(new PhotoAnswer(
                material.material(),
                true,
                answer,
                "vision+" + composer.providerName(),
                context.stream().map(PhotoSortingService::toSource).toList()));
    }

    public boolean isConfigured() {
        return identifier.isConfigured();
    }

    private static AssistantSource toSource(RetrievedItem item) {
        return new AssistantSource(
                item.itemId(),
                item.name(),
                item.destinationType() == null ? null : item.destinationType().name(),
                item.binColor() == null ? null : item.binColor().name(),
                item.sourceUrl(),
                Math.round(item.score() * 1000d) / 1000d);
    }
}
