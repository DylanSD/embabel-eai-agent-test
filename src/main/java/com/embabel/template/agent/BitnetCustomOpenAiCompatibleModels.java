package com.embabel.template.agent;

import com.embabel.agent.config.models.OpenAiCompatibleModelFactory;
import com.embabel.common.ai.model.Llm;
import com.embabel.common.ai.model.LlmOptions;
import com.embabel.common.ai.model.OptionsConverter;
import com.embabel.common.ai.model.PerTokenPricingModel;
import io.micrometer.observation.ObservationRegistry;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.DefaultChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;

import java.time.LocalDate;

@Configuration
public class BitnetCustomOpenAiCompatibleModels extends OpenAiCompatibleModelFactory {

    public BitnetCustomOpenAiCompatibleModels(
            @Value("https://n44s48888ocw00ko4csccs8g.exceptionai.com") String baseUrl,
            @Value("none") String apiKey,
            ObservationRegistry observationRegistry) {
        super(baseUrl, apiKey, null, null, observationRegistry);
    }

    @Bean
    public Llm bitnet() {
        // Call superclass method
        return openAiCompatibleLlm(
                "bitnet",
                new PerTokenPricingModel(
                        0.01,
                        0.01
                ),
                "ExceptionAI",
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
}
