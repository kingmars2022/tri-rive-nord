package com.bienvenueblainville.assistant.dto;

import com.bienvenueblainville.common.LanguageCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AssistantRequest(
        // Capped well below anything a resident would type. An unbounded
        // question is an unbounded prompt, and the prompt is what costs money.
        @NotBlank @Size(max = 300) String question,
        @NotNull LanguageCode language,

        // Which city's guide to answer from. Required rather than defaulted:
        // a missing municipality that quietly became Blainville would answer
        // three cities' residents with a fourth city's rules, and look like a
        // working feature while doing it.
        @NotBlank String municipality
) {
}
