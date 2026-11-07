package dev.legacywhisperer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.legacywhisperer.analysis.ClassInfo;
import dev.legacywhisperer.analysis.CodeAnalyzer;
import dev.legacywhisperer.analysis.ProjectMap;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CodeAnalyzerTest {

    private static ProjectMap map;

    @BeforeAll
    static void parse() throws Exception {
        map = new CodeAnalyzer().analyze(Path.of("sample-legacy"));
    }

    private static ClassInfo cls(String name) {
        return map.classes().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
    }

    private static long count(String cls, String code) {
        return cls(cls).risks().stream().filter(r -> r.code().equals(code)).count();
    }

    @Test
    void readsEverySampleClassWithoutParseErrors() {
        assertThat(map.classCount()).isEqualTo(6);
        assertThat(map.parseErrors()).isEmpty();
        assertThat(map.root()).isEqualTo("sample-legacy");
    }

    @Test
    void findsEntryPointsAndTags() {
        assertThat(cls("OrderServlet").tags()).contains("servlet");
        assertThat(cls("ReportJob").tags()).contains("main");
        assertThat(cls("OrderDao").tags()).contains("dao", "jdbc");
        assertThat(cls("Order").entryPoint()).isFalse();
    }

    @Test
    void buildsTheDependencyGraph() {
        assertThat(cls("OrderServlet").dependsOn()).containsExactly("Order", "OrderDao", "PricingEngine");
        assertThat(cls("OrderDao").dependsOn()).containsExactly("DbUtil", "Order");
        assertThat(map.usedBy().get("Order")).contains("OrderDao", "OrderServlet", "PricingEngine", "ReportJob");
    }

    @Test
    void flagsSqlBuiltByConcatenation() {
        assertThat(count("OrderDao", "SQL_CONCAT")).isEqualTo(2);
        assertThat(count("PricingEngine", "SQL_CONCAT")).isZero();
    }

    @Test
    void flagsOnlyServletFieldsThatChangeAtRuntime() {
        // requestCount and lastCustomer change per request; dao is only set once and is not flagged.
        assertThat(count("OrderServlet", "SERVLET_SHARED_STATE")).isEqualTo(2);
        assertThat(count("OrderServlet", "STATIC_DATE_FORMAT")).isEqualTo(1);
        assertThat(count("OrderServlet", "STATIC_MUTABLE")).isZero();
    }

    @Test
    void flagsSwallowedExceptionsAndDebugPrinting() {
        assertThat(count("OrderServlet", "SWALLOWED_EXCEPTION")).isEqualTo(2);
        assertThat(count("ReportJob", "SWALLOWED_EXCEPTION")).isEqualTo(1);
        assertThat(count("OrderServlet", "PRINT_DEBUG")).isEqualTo(1);
    }

    @Test
    void flagsHardcodedCredentialsAndGlobalState() {
        assertThat(count("DbUtil", "HARDCODED_SECRET")).isEqualTo(1);
        assertThat(count("DbUtil", "STATIC_MUTABLE")).isEqualTo(1);
        assertThat(count("PricingEngine", "STATIC_MUTABLE")).isEqualTo(1);
    }

    @Test
    void flagsLeakyJdbcAndComplexMethods() {
        assertThat(count("OrderDao", "RESOURCE_LEAK")).isEqualTo(1);
        assertThat(count("PricingEngine", "HIGH_COMPLEXITY")).isEqualTo(1);
        assertThat(cls("PricingEngine").methods().get(0).complexity()).isGreaterThan(10);
    }

    @Test
    void ordersMigrationFromLeavesToEntryPoints() {
        assertThat(map.migrationWaves()).hasSize(3);
        assertThat(map.migrationWaves().get(0)).containsExactly("DbUtil", "Order");
        assertThat(map.migrationWaves().get(1)).containsExactly("OrderDao", "PricingEngine");
        assertThat(map.migrationWaves().get(2)).containsExactly("OrderServlet", "ReportJob");
    }

    @Test
    void sortsRisksWithHighSeverityFirst() {
        var risks = cls("OrderServlet").risks();
        assertThat(risks.get(0).severity()).isEqualTo("high");
        assertThat(risks.get(risks.size() - 1).severity()).isEqualTo("low");
    }
}
