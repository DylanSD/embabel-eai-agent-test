package com.embabel.template.agent;

import com.embabel.agent.config.models.OpenAiCompatibleModelFactory;
import com.embabel.common.ai.model.Llm;
import com.embabel.common.ai.model.LlmOptions;
import com.embabel.common.ai.model.OptionsConverter;
import com.embabel.common.ai.model.PerTokenPricingModel;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.DefaultChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.retry.support.RetryTemplate;

import java.time.LocalDate;

@Configuration
public class InceptionCustomOpenAiCompatibleModels extends OpenAiCompatibleModelFactory {

    public InceptionCustomOpenAiCompatibleModels(
            @Value("https://api.inceptionlabs.ai") String baseUrl,
            @Value("${INCEPTION_API_KEY}") String apiKey,
            ObservationRegistry observationRegistry) {
        super(baseUrl, apiKey, null, null, observationRegistry);
    }

    @Bean
    public Llm mercury() {
        // Call superclass method
        return openAiCompatibleLlm(
                "mercury",
                new PerTokenPricingModel(
                        0.25,
                        1.0
                ),
                "Inception",
                LocalDate.of(2025, 1, 1),
                options -> OpenAiChatOptions.builder()
                .topP(1.0)
                .maxTokens(8192)
                .presencePenalty(1.5)
                .frequencyPenalty(0.0)
                .build(),
                new RetryTemplate()
        );
    }
    @Bean
    public Llm mercuryCoder() {
        // Call superclass method
        return openAiCompatibleLlm(
                "mercury-coder",
                new PerTokenPricingModel(
                        0.25,
                        1.0
                ),
                "Inception",
                LocalDate.of(2025, 1, 1),
                new OptionsConverter<ChatOptions>() {
                    @NotNull
                    @Override
                    public ChatOptions convertOptions(@NotNull LlmOptions options) {
                        return new DefaultChatOptions();
                    }
                },
                new RetryTemplate()
        );
    }
}
