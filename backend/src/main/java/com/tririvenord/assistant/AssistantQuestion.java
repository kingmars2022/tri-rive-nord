package com.tririvenord.assistant;

import com.tririvenord.common.LanguageCode;

/**
 * A question, the language it should be answered in, and the town whose guide
 * answers it - so an answer can name that town rather than assume one.
 *
 * <p>Public because {@link AnswerComposer} is: an interface nobody outside this
 * package could implement or call was not the intent, and the photo path now
 * composes answers through the same seam.
 */
public record AssistantQuestion(String text, LanguageCode language, GuideCity city) {
}
