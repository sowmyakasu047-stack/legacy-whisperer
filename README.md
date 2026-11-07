# Legacy Whisperer

Point it at an old Java codebase and get a guide a new developer can read in ten minutes:
what each class does, who uses whom, where the risky code is, and which classes to migrate first.

It parses the code with [JavaParser](https://javaparser.org), builds a dependency map, flags known
legacy problems, and (optionally) asks Claude to explain each class in plain language.

## Status

First scaffold. The code has been written and syntax-checked, but **not yet compiled or run end to end**,
because the build sandbox it was written in could not download Maven dependencies. Run `mvn test` first.
If a test fails, that is the place to start.

## Quick start

Requires Java 21 and Maven 3.9+.

```bash
mvn test                       # analyzer, path guard and report tests
mvn package                    # builds target/legacy-whisperer-0.1.0.jar

# Command line (offline, nothing leaves your machine)
java -jar target/legacy-whisperer-0.1.0.jar analyze sample-legacy --out report.md

# With AI summaries (sends the source code to the Anthropic API)
export ANTHROPIC_API_KEY=...
java -jar target/legacy-whisperer-0.1.0.jar analyze sample-legacy --ai --out report.md

# REST API on http://127.0.0.1:8080
java -jar target/legacy-whisperer-0.1.0.jar
curl "http://127.0.0.1:8080/api/map?path=sample-legacy"
curl "http://127.0.0.1:8080/api/report?path=sample-legacy"
curl "http://127.0.0.1:8080/api/report?path=sample-legacy&ai=true"
```

## What it finds

| Code | Severity | Meaning |
|---|---|---|
| `SQL_CONCAT` | high | SQL built with string concatenation (injection risk) |
| `HARDCODED_SECRET` | high | Password or key as a literal in source |
| `SERVLET_SHARED_STATE` | high | Servlet field changed at runtime; one instance serves all requests |
| `STATIC_DATE_FORMAT` | high | Shared `SimpleDateFormat` (not thread-safe) |
| `STATIC_MUTABLE` | medium | Static non-final field (global mutable state) |
| `SWALLOWED_EXCEPTION` | medium | Empty catch, or only `printStackTrace()` |
| `RESOURCE_LEAK` | medium | JDBC connection or statement opened outside try-with-resources |
| `LONG_METHOD`, `HIGH_COMPLEXITY` | medium | Methods over 60 lines or complexity above 10 |
| `PRINT_DEBUG` | low | `System.out` / `System.err` printing |

The report also lists entry points (servlets and `main` methods) and a migration order: classes
with no project dependencies first, entry points last.

## Privacy and safety

- Offline mode is the default. Source code is only sent to the Anthropic API when you pass `--ai`
  (CLI) or `ai=true` (REST), and an API key is set.
- Class source sent to the API is cut at 12,000 characters, and the prompt tells the model to treat
  the code as data, not instructions. Still, do not run `--ai` on code you are not allowed to share.
- The REST server listens on `127.0.0.1` only, and `path` must be a folder inside
  `whisperer.workspace` (default: the current directory). Symlinks and `..` cannot escape it.
- Never commit an API key. It is read from `ANTHROPIC_API_KEY`.

## Limits

- Static, heuristic analysis: it does not compile or run anything, and can produce false positives.
- Classes are matched by simple name, so two classes with the same name in different packages are merged.
- Only `.java` files are read. JSP, Ext JS, XML config and SQL files are not analyzed yet.
- The migration order treats a dependency cycle as one group in the last wave.

## Ideas for next steps

1. A React front end that draws the dependency map and lets you click a class for its summary.
2. Method-level call graph, not just class-level.
3. Parse JSP and Ext JS files and link them to the servlets they call.
4. Extract business rules from eligibility-style code into a plain-English decision table, with generated tests.
5. Cache AI summaries by file hash so re-runs are fast and cheap.
