package dev.legacywhisperer;

import dev.legacywhisperer.ai.Summarizers;
import dev.legacywhisperer.report.AnalysisService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Command line mode: {@code analyze <folder> [--ai] [--out report.md]}. */
public final class Cli {

    private Cli() {
    }

    public static int run(String[] args) {
        Path dir = null;
        Path out = null;
        boolean ai = false;
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--ai" -> ai = true;
                case "--out" -> {
                    if (i + 1 >= args.length) return usage("--out needs a file name");
                    out = Path.of(args[++i]);
                }
                default -> {
                    if (dir != null) return usage("unexpected argument: " + args[i]);
                    dir = Path.of(args[i]);
                }
            }
        }
        if (dir == null) return usage("missing folder to analyze");
        if (!Files.isDirectory(dir)) return usage("not a folder: " + dir);

        String key = System.getenv("ANTHROPIC_API_KEY");
        String model = System.getenv().getOrDefault("WHISPERER_MODEL", "claude-sonnet-5-5");
        boolean haveKey = key != null && !key.isBlank();
        if (ai && !haveKey) {
            System.err.println("ANTHROPIC_API_KEY is not set, so offline summaries will be used.");
        } else if (ai) {
            System.err.println("AI mode: the source code of " + dir + " will be sent to the Anthropic API.");
        }

        AnalysisService service = new AnalysisService(Summarizers.create(key, model));
        String markdown = service.report(dir, ai);
        try {
            if (out != null) {
                Files.writeString(out, markdown, StandardCharsets.UTF_8);
                System.err.println("Wrote " + out);
            } else {
                System.out.println(markdown);
            }
        } catch (IOException e) {
            System.err.println("Could not write the report: " + e.getMessage());
            return 1;
        }
        return 0;
    }

    private static int usage(String problem) {
        System.err.println(problem);
        System.err.println("Usage: java -jar legacy-whisperer.jar analyze <folder> [--ai] [--out report.md]");
        return 2;
    }
}
