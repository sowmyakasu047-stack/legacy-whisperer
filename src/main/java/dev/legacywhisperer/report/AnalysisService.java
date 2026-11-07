package dev.legacywhisperer.report;

import dev.legacywhisperer.ai.OfflineSummarizer;
import dev.legacywhisperer.ai.Summarizer;
import dev.legacywhisperer.analysis.ClassInfo;
import dev.legacywhisperer.analysis.CodeAnalyzer;
import dev.legacywhisperer.analysis.ProjectMap;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ties the analyzer, the summarizers and the report together. Works with or without Spring. */
@Service
public class AnalysisService {

    private final CodeAnalyzer analyzer = new CodeAnalyzer();
    private final Summarizer offline = new OfflineSummarizer();
    private final Summarizer configured;

    public AnalysisService(Summarizer configured) {
        this.configured = configured;
    }

    public ProjectMap map(Path root) {
        try {
            return analyzer.analyze(root);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * @param useAi when true, the configured summarizer is used (which may send source code to the Anthropic API);
     *              when false, only the offline summarizer runs and nothing leaves the machine
     */
    public String report(Path root, boolean useAi) {
        ProjectMap map = map(root);
        Summarizer summarizer = useAi ? configured : offline;
        Map<String, String> summaries = new LinkedHashMap<>();
        for (ClassInfo c : map.classes()) {
            List<String> usedBy = map.usedBy().getOrDefault(c.name(), List.of());
            summaries.put(c.name(), summarizer.summarize(c, usedBy, readSource(root, c)));
        }
        return MarkdownReport.render(map, summaries, summarizer.name());
    }

    private static String readSource(Path root, ClassInfo c) {
        try {
            return new String(Files.readAllBytes(root.resolve(c.file())), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
