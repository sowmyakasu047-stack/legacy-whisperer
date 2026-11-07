package dev.legacywhisperer.analysis;

/** A hotspot found by static analysis. Severity is "high", "medium" or "low". */
public record Risk(String code, String severity, String message, int line) {

    public int rank() {
        return switch (severity) {
            case "high" -> 0;
            case "medium" -> 1;
            default -> 2;
        };
    }
}
