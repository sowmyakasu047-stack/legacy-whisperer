package dev.legacywhisperer.web;

import dev.legacywhisperer.analysis.ProjectMap;
import dev.legacywhisperer.report.AnalysisService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.UncheckedIOException;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AnalyzeController {

    private final AnalysisService service;
    private final Workspace workspace;

    public AnalyzeController(AnalysisService service, Workspace workspace) {
        this.service = service;
        this.workspace = workspace;
    }

    /** JSON map of the project: classes, risks, who uses whom, migration waves. */
    @GetMapping("/map")
    public ProjectMap map(@RequestParam String path) {
        return service.map(workspace.resolve(path));
    }

    /** Markdown guide. AI summaries are opt-in because they send source code to the Anthropic API. */
    @GetMapping(value = "/report", produces = "text/markdown;charset=UTF-8")
    public String report(@RequestParam String path, @RequestParam(defaultValue = "false") boolean ai) {
        return service.report(workspace.resolve(path), ai);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(UncheckedIOException.class)
    ResponseEntity<Map<String, String>> ioProblem(UncheckedIOException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "could not read the project files"));
    }
}
