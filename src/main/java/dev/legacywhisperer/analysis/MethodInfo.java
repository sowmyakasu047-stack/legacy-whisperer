package dev.legacywhisperer.analysis;

/** One method of a legacy class, with the size and branching numbers that make it hard to change. */
public record MethodInfo(String name, String signature, int startLine, int lines, int complexity) {
}
