package dev.legacywhisperer.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Keeps API callers inside one base folder, so the endpoint cannot be used to read arbitrary files. */
@Component
public class Workspace {

    private final Path base;

    public Workspace(@Value("${whisperer.workspace:.}") String base) throws IOException {
        this.base = Path.of(base).toRealPath();
    }

    /** Resolves a folder relative to the workspace, or throws IllegalArgumentException. */
    public Path resolve(String relative) {
        if (relative == null || relative.isBlank()) {
            throw new IllegalArgumentException("path is required");
        }
        Path resolved;
        try {
            resolved = base.resolve(relative).toRealPath();
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("folder not found inside the workspace");
        }
        if (!resolved.startsWith(base)) {
            throw new IllegalArgumentException("path is outside the workspace");
        }
        if (!Files.isDirectory(resolved)) {
            throw new IllegalArgumentException("path is not a folder");
        }
        return resolved;
    }
}
