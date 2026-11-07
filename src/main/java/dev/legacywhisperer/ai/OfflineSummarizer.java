package dev.legacywhisperer.ai;

import dev.legacywhisperer.analysis.ClassInfo;
import dev.legacywhisperer.analysis.MethodInfo;
import dev.legacywhisperer.analysis.Risk;

import java.util.Comparator;
import java.util.List;

/** Explains a class from the static analysis alone. No network, no API key, no source leaves the machine. */
public class OfflineSummarizer implements Summarizer {

    @Override
    public String name() {
        return "offline heuristics (no AI)";
    }

    @Override
    public String summarize(ClassInfo c, List<String> usedBy, String source) {
        StringBuilder sb = new StringBuilder();
        sb.append("`").append(c.name()).append("` is ").append(role(c)).append(". It has ")
                .append(c.lines()).append(" lines and ").append(c.methods().size()).append(" methods.");

        c.methods().stream().max(Comparator.comparingInt(MethodInfo::complexity)).ifPresent(m -> {
            if (m.complexity() > 1) {
                sb.append(" The most complex method is `").append(m.name()).append("()` (complexity ")
                        .append(m.complexity()).append(", ").append(m.lines()).append(" lines).");
            }
        });

        if (!c.dependsOn().isEmpty()) {
            sb.append(" It depends on ").append(String.join(", ", c.dependsOn())).append(".");
        }
        if (!usedBy.isEmpty()) {
            sb.append(" It is used by ").append(String.join(", ", usedBy)).append(".");
        } else if (!c.entryPoint()) {
            sb.append(" Nothing in this project uses it, so it may be dead code or called from outside (JSP, config, reflection).");
        }

        long high = c.risks().stream().filter(r -> r.severity().equals("high")).count();
        long medium = c.risks().stream().filter(r -> r.severity().equals("medium")).count();
        long low = c.risks().stream().filter(r -> r.severity().equals("low")).count();
        if (c.risks().isEmpty()) {
            sb.append(" Static analysis found no hotspots.");
        } else {
            sb.append(" Static analysis found ").append(c.risks().size()).append(" hotspot(s): ")
                    .append(high).append(" high, ").append(medium).append(" medium, ").append(low).append(" low.");
            Risk top = c.risks().get(0);
            sb.append(" Most serious: ").append(top.message()).append(" (line ").append(top.line()).append(").");
        }
        sb.append("\n\n_Offline summary. Run with AI enabled for a business-level explanation._");
        return sb.toString();
    }

    private static String role(ClassInfo c) {
        if (c.tags().contains("servlet")) {
            return "an HTTP entry point (a servlet that receives web requests)";
        }
        if (c.tags().contains("main")) {
            return "a command-line or batch entry point (it has a main method)";
        }
        if (c.tags().contains("dao") && c.tags().contains("jdbc")) {
            return "a data-access class that talks to the database through raw JDBC";
        }
        if (c.tags().contains("jdbc")) {
            return "a class that works with the database through JDBC";
        }
        if (c.tags().contains("dao")) {
            return "a data-access class";
        }
        return "a " + c.kind();
    }
}
