package com.tririvenord.assistant;

import com.anthropic.client.AnthropicClient;
import com.tririvenord.common.LanguageCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClaudeAnswerComposerTest {
    @Test
    void reportsTemplateWhenTheProviderFails() {
        AnthropicClient client = mock(AnthropicClient.class);
        when(client.messages()).thenThrow(new IllegalStateException("Provider unavailable"));
        ClaudeAnswerComposer composer = new ClaudeAnswerComposer(client, "test-model",
                new TemplateAnswerComposer());
        AnswerComposer.Composition result = composer.composeWithMetadata(
                new AssistantQuestion("cardboard", LanguageCode.en,
                        new GuideCity("Boisbriand", "ville.boisbriand.qc.ca")),
                List.of(new RetrievedItem(1L, LanguageCode.en, "Cardboard", "Use the blue bin.",
                        null, null, null, null, 1.0)));
        assertThat(result.provider()).isEqualTo("template");
        assertThat(result.text()).contains("Use the blue bin.");
    }

    /** The model was told it served Blainville, and to send residents to blainville.ca, whatever town asked. */
    @Test
    void theInstructionsNameTheTownAskedAndItsWebsite() {
        String system = ClaudeAnswerComposer.systemFor(new GuideCity("Rosemere", "ville.rosemere.qc.ca"));

        assertThat(system).contains("residents of Rosemere, Quebec").contains("check ville.rosemere.qc.ca")
                .doesNotContain("Blainville").doesNotContain("blainville.ca");
        assertThat(ClaudeAnswerComposer.systemFor(new GuideCity("Rosemere", null)))
                .contains("check the town's website");
    }
}
