package dev.legacywhisperer.analysis;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads a folder of legacy Java sources and builds a {@link ProjectMap}.
 *
 * <p>Everything here is static, heuristic analysis: it never runs or compiles the code, and it
 * matches classes by simple name, so two classes with the same name in different packages are
 * treated as one.
 */
public class CodeAnalyzer {

    private static final Pattern SQL = Pattern.compile(
            "\\b(select\\b.+?\\bfrom\\b|insert\\s+into\\b|update\\b.+?\\bset\\b|delete\\s+from\\b)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SECRET_NAME =
            Pattern.compile("(password|passwd|pwd|secret|api_?key|token)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DAO_NAME = Pattern.compile(".*(dao|repository)$", Pattern.CASE_INSENSITIVE);
    private static final Set<String> JDBC_CALLS =
            Set.of("getConnection", "createStatement", "prepareStatement", "prepareCall");
    private static final Set<String> JDBC_TYPES =
            Set.of("Connection", "Statement", "PreparedStatement", "ResultSet", "DriverManager");
    private static final Set<String> IGNORED_DIRS = Set.of("target", "build", ".git", "node_modules", ".idea");
    private static final int LONG_METHOD_LINES = 60;
    private static final int HIGH_COMPLEXITY = 10;

    private final JavaParser parser;

    public CodeAnalyzer() {
        ParserConfiguration config = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        this.parser = new JavaParser(config);
    }

    public ProjectMap analyze(Path rootDir) throws IOException {
        Path root = rootDir.toAbsolutePath().normalize();
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !isIgnored(root.relativize(p)))
                    .sorted()
                    .toList();
        }

        record Parsed(String file, CompilationUnit unit) {
        }
        List<Parsed> parsed = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (Path file : files) {
            String rel = root.relativize(file).toString().replace('\\', '/');
            ParseResult<CompilationUnit> result = parser.parse(file);
            if (!result.isSuccessful()) {
                errors.add(rel + ": " + firstProblem(result));
            }
            result.getResult().ifPresent(cu -> parsed.add(new Parsed(rel, cu)));
        }

        Set<String> known = new TreeSet<>();
        for (Parsed p : parsed) {
            p.unit().getTypes().forEach(t -> known.add(t.getNameAsString()));
        }

        List<ClassInfo> classes = new ArrayList<>();
        for (Parsed p : parsed) {
            for (TypeDeclaration<?> type : p.unit().getTypes()) {
                classes.add(toClassInfo(p.file(), p.unit(), type, known));
            }
        }
        classes.sort(Comparator.comparing(ClassInfo::name));

        Map<String, List<String>> usedBy = new TreeMap<>();
        for (ClassInfo c : classes) {
            for (String dep : c.dependsOn()) {
                usedBy.computeIfAbsent(dep, k -> new ArrayList<>()).add(c.name());
            }
        }

        int totalLines = classes.stream().mapToInt(ClassInfo::lines).sum();
        String rootName = root.getFileName() == null ? root.toString() : root.getFileName().toString();
        return new ProjectMap(rootName, parsed.size(), classes.size(), totalLines,
                classes, usedBy, migrationWaves(classes), errors);
    }

    // ---------------------------------------------------------------- per class

    private ClassInfo toClassInfo(String file, CompilationUnit cu, TypeDeclaration<?> td, Set<String> known) {
        String pkg = cu.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");

        String superType = (td instanceof ClassOrInterfaceDeclaration c
                && !c.isInterface() && !c.getExtendedTypes().isEmpty())
                ? c.getExtendedTypes().get(0).getNameAsString() : null;

        List<String> interfaces = new ArrayList<>();
        if (td instanceof ClassOrInterfaceDeclaration c) {
            if (c.isInterface()) {
                c.getExtendedTypes().forEach(t -> interfaces.add(t.getNameAsString()));
            }
            c.getImplementedTypes().forEach(t -> interfaces.add(t.getNameAsString()));
        } else if (td instanceof EnumDeclaration e) {
            e.getImplementedTypes().forEach(t -> interfaces.add(t.getNameAsString()));
        } else if (td instanceof RecordDeclaration r) {
            r.getImplementedTypes().forEach(t -> interfaces.add(t.getNameAsString()));
        }

        List<String> annotations = td.getAnnotations().stream().map(a -> a.getNameAsString()).toList();

        List<MethodInfo> methods = td.getMethods().stream()
                .map(m -> new MethodInfo(m.getNameAsString(), m.getDeclarationAsString(false, false, true),
                        lineOf(m), spanOf(m), complexity(m)))
                .toList();

        // Classes this one refers to, by simple name (types and static-call targets).
        Set<String> refs = new TreeSet<>();
        td.findAll(ClassOrInterfaceType.class).forEach(t -> refs.add(t.getNameAsString()));
        td.findAll(NameExpr.class).forEach(n -> refs.add(n.getNameAsString()));
        refs.retainAll(known);
        refs.remove(td.getNameAsString());

        boolean servlet = (superType != null && (superType.equals("HttpServlet") || superType.equals("GenericServlet")))
                || annotations.contains("WebServlet");
        boolean hasMain = td.getMethods().stream().anyMatch(m -> m.getNameAsString().equals("main") && m.isStatic());
        boolean jdbc = cu.getImports().stream()
                .anyMatch(i -> i.getNameAsString().startsWith("java.sql") || i.getNameAsString().startsWith("javax.sql"))
                || td.findAll(ClassOrInterfaceType.class).stream().anyMatch(t -> JDBC_TYPES.contains(t.getNameAsString()))
                || td.findAll(NameExpr.class).stream().anyMatch(n -> n.getNameAsString().equals("DriverManager"));

        Set<String> tags = new LinkedHashSet<>();
        if (servlet) tags.add("servlet");
        if (hasMain) tags.add("main");
        if (jdbc) tags.add("jdbc");
        if (DAO_NAME.matcher(td.getNameAsString()).matches()) tags.add("dao");

        List<Risk> risks = findRisks(td, servlet, methods);

        return new ClassInfo(td.getNameAsString(), pkg, kindOf(td), file, spanOf(td), superType, interfaces,
                annotations, List.copyOf(tags), servlet || hasMain, methods, List.copyOf(refs), risks);
    }

    private static String kindOf(TypeDeclaration<?> td) {
        if (td instanceof ClassOrInterfaceDeclaration c) return c.isInterface() ? "interface" : "class";
        if (td instanceof EnumDeclaration) return "enum";
        if (td instanceof RecordDeclaration) return "record";
        return "type";
    }

    // ---------------------------------------------------------------- risks

    private List<Risk> findRisks(TypeDeclaration<?> td, boolean servlet, List<MethodInfo> methods) {
        List<Risk> risks = new ArrayList<>();

        // SQL built by concatenation.
        for (BinaryExpr b : td.findAll(BinaryExpr.class)) {
            if (b.getOperator() != BinaryExpr.Operator.PLUS) continue;
            if (b.getParentNode().orElse(null) instanceof BinaryExpr parent
                    && parent.getOperator() == BinaryExpr.Operator.PLUS) continue;
            List<Expression> operands = new ArrayList<>();
            flatten(b, operands);
            boolean sql = operands.stream()
                    .anyMatch(e -> e instanceof StringLiteralExpr s && SQL.matcher(s.asString()).find());
            boolean dynamic = operands.stream().anyMatch(e -> !(e instanceof LiteralExpr));
            if (sql && dynamic) {
                risks.add(new Risk("SQL_CONCAT", "high",
                        "SQL is built by string concatenation (injection risk); use PreparedStatement parameters",
                        lineOf(b)));
            }
        }

        // Names that are assigned or incremented somewhere in the class.
        Set<String> assigned = new HashSet<>();
        for (AssignExpr a : td.findAll(AssignExpr.class)) {
            Expression target = a.getTarget();
            if (target instanceof NameExpr n) {
                assigned.add(n.getNameAsString());
            } else if (target instanceof FieldAccessExpr fa && fa.getScope() instanceof ThisExpr) {
                assigned.add(fa.getNameAsString());
            }
        }
        for (UnaryExpr u : td.findAll(UnaryExpr.class)) {
            UnaryExpr.Operator op = u.getOperator();
            boolean change = op == UnaryExpr.Operator.PREFIX_INCREMENT || op == UnaryExpr.Operator.POSTFIX_INCREMENT
                    || op == UnaryExpr.Operator.PREFIX_DECREMENT || op == UnaryExpr.Operator.POSTFIX_DECREMENT;
            if (change && u.getExpression() instanceof NameExpr n) {
                assigned.add(n.getNameAsString());
            }
        }

        for (FieldDeclaration f : td.getFields()) {
            for (VariableDeclarator v : f.getVariables()) {
                String name = v.getNameAsString();
                if (servlet && !f.isStatic() && !f.isFinal() && assigned.contains(name)) {
                    risks.add(new Risk("SERVLET_SHARED_STATE", "high",
                            "Servlet field '" + name + "' is changed at runtime but one servlet instance serves all requests (not thread-safe)",
                            lineOf(f)));
                }
                if (f.isStatic() && !f.isFinal()) {
                    risks.add(new Risk("STATIC_MUTABLE", "medium",
                            "Static non-final field '" + name + "' is global mutable state shared by every caller",
                            lineOf(f)));
                }
                if (f.isStatic() && v.getType().asString().endsWith("SimpleDateFormat")) {
                    risks.add(new Risk("STATIC_DATE_FORMAT", "high",
                            "Shared SimpleDateFormat '" + name + "' is not thread-safe; use java.time.DateTimeFormatter",
                            lineOf(f)));
                }
            }
        }

        // Swallowed exceptions.
        for (CatchClause c : td.findAll(CatchClause.class)) {
            var stmts = c.getBody().getStatements();
            boolean onlyPrintStackTrace = !stmts.isEmpty() && stmts.stream().allMatch(s ->
                    s instanceof ExpressionStmt es && es.getExpression() instanceof MethodCallExpr m
                            && m.getNameAsString().equals("printStackTrace"));
            if (stmts.isEmpty() || onlyPrintStackTrace) {
                risks.add(new Risk("SWALLOWED_EXCEPTION", "medium",
                        stmts.isEmpty() ? "Empty catch block hides failures"
                                : "Catch block only calls printStackTrace(); the failure is not handled or logged properly",
                        lineOf(c)));
            }
        }

        // Hard-coded credentials.
        for (VariableDeclarator v : td.findAll(VariableDeclarator.class)) {
            if (SECRET_NAME.matcher(v.getNameAsString()).find()
                    && v.getInitializer().orElse(null) instanceof StringLiteralExpr s
                    && !s.asString().isEmpty()) {
                risks.add(new Risk("HARDCODED_SECRET", "high",
                        "'" + v.getNameAsString() + "' is a hard-coded credential in source", lineOf(v)));
            }
        }
        for (MethodCallExpr m : td.findAll(MethodCallExpr.class)) {
            if (m.getNameAsString().equals("getConnection")
                    && m.getScope().map(Node::toString).orElse("").equals("DriverManager")
                    && m.getArguments().size() == 3
                    && m.getArgument(2) instanceof StringLiteralExpr pw && !pw.asString().isEmpty()) {
                risks.add(new Risk("HARDCODED_SECRET", "high",
                        "Database password is a literal in DriverManager.getConnection(...)", lineOf(m)));
            }
        }

        // JDBC resources opened outside try-with-resources (one finding per class).
        List<MethodCallExpr> leaky = td.findAll(MethodCallExpr.class).stream()
                .filter(m -> JDBC_CALLS.contains(m.getNameAsString()))
                .filter(m -> !inTryResources(m))
                .toList();
        if (!leaky.isEmpty()) {
            risks.add(new Risk("RESOURCE_LEAK", "medium",
                    leaky.size() + " JDBC call(s) open connections or statements outside try-with-resources; they can leak when an error occurs",
                    lineOf(leaky.get(0))));
        }

        // Size and branching.
        for (MethodInfo m : methods) {
            if (m.lines() > LONG_METHOD_LINES) {
                risks.add(new Risk("LONG_METHOD", "medium",
                        "Method " + m.name() + "() is " + m.lines() + " lines long", m.startLine()));
            }
            if (m.complexity() > HIGH_COMPLEXITY) {
                risks.add(new Risk("HIGH_COMPLEXITY", "medium",
                        "Method " + m.name() + "() has complexity " + m.complexity() + "; hard to test and change safely",
                        m.startLine()));
            }
        }

        // Debug printing (one finding per class).
        List<MethodCallExpr> prints = td.findAll(MethodCallExpr.class).stream()
                .filter(m -> m.getScope().map(s -> s instanceof FieldAccessExpr fa
                        && fa.getScope().toString().equals("System")
                        && (fa.getNameAsString().equals("out") || fa.getNameAsString().equals("err"))).orElse(false))
                .toList();
        if (!prints.isEmpty()) {
            risks.add(new Risk("PRINT_DEBUG", "low",
                    prints.size() + " System.out/err print statement(s); replace with a logger", lineOf(prints.get(0))));
        }

        risks.sort(Comparator.comparingInt(Risk::rank).thenComparingInt(Risk::line));
        return risks;
    }

    private static void flatten(Expression e, List<Expression> out) {
        if (e instanceof BinaryExpr b && b.getOperator() == BinaryExpr.Operator.PLUS) {
            flatten(b.getLeft(), out);
            flatten(b.getRight(), out);
        } else {
            out.add(e);
        }
    }

    private static boolean inTryResources(MethodCallExpr call) {
        Node node = call;
        while (node.getParentNode().isPresent()) {
            Node parent = node.getParentNode().get();
            if (parent instanceof TryStmt t) {
                for (Expression resource : t.getResources()) {
                    if (resource.findAll(MethodCallExpr.class).stream().anyMatch(c -> c == call)) {
                        return true;
                    }
                }
            }
            node = parent;
        }
        return false;
    }

    private static int complexity(MethodDeclaration m) {
        int c = 1;
        c += m.findAll(IfStmt.class).size();
        c += m.findAll(ForStmt.class).size();
        c += m.findAll(ForEachStmt.class).size();
        c += m.findAll(WhileStmt.class).size();
        c += m.findAll(DoStmt.class).size();
        c += m.findAll(CatchClause.class).size();
        c += m.findAll(ConditionalExpr.class).size();
        c += (int) m.findAll(SwitchEntry.class).stream().filter(e -> !e.getLabels().isEmpty()).count();
        c += (int) m.findAll(BinaryExpr.class).stream()
                .filter(b -> b.getOperator() == BinaryExpr.Operator.AND || b.getOperator() == BinaryExpr.Operator.OR)
                .count();
        return c;
    }

    // ---------------------------------------------------------------- migration order

    /**
     * Groups classes so each wave depends only on earlier waves: migrate the leaves first.
     * Classes caught in a dependency cycle are placed together in the last wave.
     */
    static List<List<String>> migrationWaves(List<ClassInfo> classes) {
        Map<String, Set<String>> deps = new LinkedHashMap<>();
        classes.forEach(c -> deps.put(c.name(), new TreeSet<>(c.dependsOn())));
        Set<String> done = new HashSet<>();
        List<List<String>> waves = new ArrayList<>();
        while (done.size() < deps.size()) {
            List<String> wave = deps.entrySet().stream()
                    .filter(e -> !done.contains(e.getKey()) && done.containsAll(e.getValue()))
                    .map(Map.Entry::getKey)
                    .sorted()
                    .toList();
            if (wave.isEmpty()) {
                wave = deps.keySet().stream().filter(k -> !done.contains(k)).sorted().toList();
            }
            waves.add(wave);
            done.addAll(wave);
        }
        return waves;
    }

    // ---------------------------------------------------------------- small helpers

    private static boolean isIgnored(Path relative) {
        for (Path part : relative) {
            if (IGNORED_DIRS.contains(part.toString())) return true;
        }
        return false;
    }

    private static String firstProblem(ParseResult<?> result) {
        String msg = result.getProblems().stream().findFirst().map(Problem::getMessage).orElse("unknown parse error");
        int nl = msg.indexOf('\n');
        return nl > 0 ? msg.substring(0, nl) : msg;
    }

    private static int lineOf(Node n) {
        return n.getBegin().map(p -> p.line).orElse(0);
    }

    private static int spanOf(Node n) {
        return n.getRange().map(r -> r.end.line - r.begin.line + 1).orElse(0);
    }
}
