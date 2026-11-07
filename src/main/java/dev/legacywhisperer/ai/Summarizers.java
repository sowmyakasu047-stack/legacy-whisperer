package dev.legacywhisperer.ai;

/** Picks the summarizer: Claude when an API key is configured, offline heuristics otherwise. */
public final class Summarizers {

    private static final String API_BASE_URL = "https://api.anthropic.com";

    private Summarizers() {
    }

    public static Summarizer create(String apiKey, String model) {
        OfflineSummarizer offline = new OfflineSummarizer();
        if (apiKey == null || apiKey.isBlank()) {
            return offline;
        }
        return new ClaudeSummarizer(apiKey.trim(), model, API_BASE_URL, offline);
    }
}
