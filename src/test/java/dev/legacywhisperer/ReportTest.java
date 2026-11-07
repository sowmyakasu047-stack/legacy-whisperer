package dev.legacywhisperer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.legacywhisperer.ai.Summarizers;
import dev.legacywhisperer.analysis.CodeAnalyzer;
import dev.legacywhisperer.analysis.ProjectMap;
import dev.legacywhisperer.ai.ClaudeSummarizer;
import dev.legacywhisperer.ai.OfflineSummarizer;
import dev.legacywhisperer.report.AnalysisService;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportTest {

    private final Path sample = Path.of("sample-legacy");

    @Test
    void offlineReportCoversEverySection() {
        AnalysisService service = new AnalysisService(new OfflineSummarizer());
        String md = service.report(sample, false);

        assertThat(md).contains("# Legacy Whisperer report: sample-legacy");
        assertThat(md).contains("## At a glance", "## Entry points", "## Risk hotspots",
                "## Suggested migration order", "## Class guide");
        assertThat(md).contains("`OrderServlet` is an HTTP entry point");
        assertThat(md).contains("SQL is built by string concatenation");
        assertThat(md).contains("**Start here** (no dependencies on other project classes): `DbUtil`, `Order`");
        // The report must not leak the absolute path of the analyzed folder.
        assertThat(md).doesNotContain(sample.toAbsolutePath().toString());
    }

    @Test
    void withoutAnApiKeyTheOfflineEngineIsUsed() {
        assertThat(Summarizers.create("", "m")).isInstanceOf(OfflineSummarizer.class);
        assertThat(Summarizers.create(null, "m")).isInstanceOf(OfflineSummarizer.class);
        assertThat(Summarizers.create("sk-test", "m")).isInstanceOf(ClaudeSummarizer.class);
    }

    @Test
    void failedAiCallFallsBackToOfflineSummary() throws Exception {
        // Port 9 (discard) on localhost refuses connections, so the call fails fast.
        ClaudeSummarizer claude = new ClaudeSummarizer("sk-test", "m", "http://127.0.0.1:9", new OfflineSummarizer());
        ProjectMap map = new CodeAnalyzer().analyze(sample);
        var order = map.classes().stream().filter(c -> c.name().equals("Order")).findFirst().orElseThrow();

        String text = claude.summarize(order, List.of(), "class Order {}");

        assertThat(text).contains("`Order` is a class");
        assertThat(text).contains("showing the offline summary");
    }
}
