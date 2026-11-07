package dev.legacywhisperer.report;

import dev.legacywhisperer.analysis.ClassInfo;
import dev.legacywhisperer.analysis.ProjectMap;
import dev.legacywhisperer.analysis.Risk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Renders a {@link ProjectMap} plus per-class summaries as a Markdown guide for a new developer. */
public final class MarkdownReport {

    private static final int MAX_RISK_ROWS = 30;

    private MarkdownReport() {
    }

    public static String render(ProjectMap map, Map<String, String> summaries, String engine) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Legacy Whisperer report: ").append(map.root()).append("\n\n");
        sb.append("Summaries by: ").append(engine).append("\n\n");

        List<Risk> allRisks = map.classes().stream().flatMap(c -> c.risks().stream()).toList();
        long high = allRisks.stream().filter(r -> r.severity().equals("high")).count();
        long medium = allRisks.stream().filter(r -> r.severity().equals("medium")).count();
        long low = allRisks.stream().filter(r -> r.severity().equals("low")).count();
        long entries = map.classes().stream().filter(ClassInfo::entryPoint).count();

        sb.append("## At a glance\n\n");
        sb.append("- ").append(map.fileCount()).append(" files, ").append(map.classCount())
                .append(" classes, ").append(map.totalLines()).append(" lines\n");
        sb.append("- ").append(entries).append(" entry point(s)\n");
        sb.append("- ").append(allRisks.size()).append(" hotspots: ").append(high).append(" high, ")
                .append(medium).append(" medium, ").append(low).append(" low\n\n");

        sb.append("## Entry points\n\n");
        List<ClassInfo> entryPoints = map.classes().stream().filter(ClassInfo::entryPoint).toList();
        if (entryPoints.isEmpty()) {
            sb.append("None detected (no servlets or main methods).\n\n");
        } else {
            for (ClassInfo c : entryPoints) {
                sb.append("- `").append(c.name()).append("` (").append(String.join(", ", c.tags()))
                        .append(") in `").append(c.file()).append("`\n");
            }
            sb.append("\n");
        }

        sb.append("## Risk hotspots\n\n");
        if (allRisks.isEmpty()) {
            sb.append("No hotspots found.\n\n");
        } else {
            record Row(String cls, Risk risk) {
            }
            List<Row> rows = new ArrayList<>();
            map.classes().forEach(c -> c.risks().forEach(r -> rows.add(new Row(c.name(), r))));
            rows.sort(Comparator.<Row>comparingInt(r -> r.risk().rank()).thenComparing(Row::cls));
            sb.append("| Severity | Class | Line | Finding |\n|---|---|---|---|\n");
            for (Row row : rows.stream().limit(MAX_RISK_ROWS).toList()) {
                sb.append("| ").append(row.risk().severity()).append(" | `").append(row.cls()).append("` | ")
                        .append(row.risk().line()).append(" | ").append(cell(row.risk().message())).append(" |\n");
            }
            if (rows.size() > MAX_RISK_ROWS) {
                sb.append("\n").append(rows.size() - MAX_RISK_ROWS).append(" more not shown.\n");
            }
            sb.append("\n");
        }

        sb.append("## Suggested migration order\n\n");
        sb.append("Move the classes with the fewest dependencies first, so each step only needs code that is already migrated. ")
                .append("Classes caught in a dependency cycle are grouped together in the last wave.\n\n");
        int n = 1;
        for (List<String> wave : map.migrationWaves()) {
            sb.append(n).append(". ").append(n == 1 ? "**Start here** " : "**Next** ")
                    .append("(").append(n == 1 ? "no dependencies on other project classes" : "depends only on earlier waves")
                    .append("): ").append(String.join(", ", wave.stream().map(s -> "`" + s + "`").toList())).append("\n");
            n++;
        }
        sb.append("\n");

        sb.append("## Class guide\n\n");
        for (ClassInfo c : map.classes()) {
            sb.append("### ").append(c.name()).append("\n\n");
            sb.append("`").append(c.file()).append("`");
            if (!c.tags().isEmpty()) {
                sb.append(" · ").append(String.join(", ", c.tags()));
            }
            sb.append("\n\n");
            sb.append(summaries.getOrDefault(c.name(), "")).append("\n\n");
        }

        if (!map.parseErrors().isEmpty()) {
            sb.append("## Files the parser could not fully read\n\n");
            map.parseErrors().forEach(e -> sb.append("- ").append(e).append("\n"));
            sb.append("\n");
        }
        return sb.toString();
    }

    private static String cell(String text) {
        return text.replace("|", "\\|").replace("\n", " ");
    }
}
