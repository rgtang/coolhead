package dev.you.coolhead.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * True when a real provider key is configured. Spring AI still boots with a placeholder, but we must not
 * call the network when the key is blank or the default {@code missing} sentinel.
 */
@Component
public class LlmAvailability {

    private final boolean available;

    public LlmAvailability(@Value("${spring.ai.openai.api-key:}") String apiKey) {
        String k = apiKey == null ? "" : apiKey.trim();
        this.available = !k.isEmpty() && !"missing".equalsIgnoreCase(k);
    }

    public boolean available() {
        return available;
    }
}
