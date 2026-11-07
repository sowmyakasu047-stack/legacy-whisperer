package dev.legacywhisperer.ai;

import dev.legacywhisperer.analysis.ClassInfo;

import java.util.List;

/** Turns one analyzed class into a short plain-language explanation (Markdown). */
public interface Summarizer {

    /** Human-readable engine name shown in the report header. */
    String name();

    String summarize(ClassInfo cls, List<String> usedBy, String source);
}
