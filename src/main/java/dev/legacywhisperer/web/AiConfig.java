package dev.legacywhisperer.web;

import dev.legacywhisperer.ai.Summarizer;
import dev.legacywhisperer.ai.Summarizers;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    Summarizer summarizer(@Value("${whisperer.ai.api-key:}") String apiKey,
                          @Value("${whisperer.ai.model:claude-sonnet-5-5}") String model) {
        return Summarizers.create(apiKey, model);
    }
}
