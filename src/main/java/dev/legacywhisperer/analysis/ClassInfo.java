package dev.legacywhisperer.analysis;

import java.util.List;

/** Everything the analyzer learned about one top-level type. */
public record ClassInfo(
        String name,
        String packageName,
        String kind,
        String file,
        int lines,
        String superType,
        List<String> interfaces,
        List<String> annotations,
        List<String> tags,
        boolean entryPoint,
        List<MethodInfo> methods,
        List<String> dependsOn,
        List<Risk> risks) {
}
