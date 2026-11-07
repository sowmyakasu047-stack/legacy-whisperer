package dev.legacywhisperer.analysis;

import java.util.List;
import java.util.Map;

/**
 * The whole-project picture: classes, who uses whom, and a suggested migration order.
 *
 * @param root           name of the analyzed folder (not the full path)
 * @param usedBy         for each class, the classes that depend on it
 * @param migrationWaves classes grouped so that each wave depends only on earlier waves
 * @param parseErrors    files the parser could not fully read
 */
public record ProjectMap(
        String root,
        int fileCount,
        int classCount,
        int totalLines,
        List<ClassInfo> classes,
        Map<String, List<String>> usedBy,
        List<List<String>> migrationWaves,
        List<String> parseErrors) {
}
