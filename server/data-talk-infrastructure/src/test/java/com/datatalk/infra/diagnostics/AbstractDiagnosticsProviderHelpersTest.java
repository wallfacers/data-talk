package com.datatalk.infra.diagnostics;

import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.domain.diagnostics.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractDiagnosticsProviderHelpersTest {

    private final TestProvider provider = new TestProvider();

    @Test
    void mapTabularPlanToNodes_inferTreeFromIdPrefix() {
        var rows = List.<Map<String, Object>>of(
            row("id", "TableReader_7", "estRows", "3323", "access object", "", "operator info", "data:Selection_6"),
            row("id", "└─Selection_6",  "estRows", "3323", "access object", "", "operator info", "lt(t.a, 1)"),
            row("id", "  └─TableFullScan_5", "estRows", "10000", "access object", "table:t", "operator info", "keep order:false")
        );
        var layout = new TabularLayout("id", null, "(\\w+)_\\d+", "estRows", "access object", "operator info");

        var nodes = provider.mapTabularPlanToNodes(rows, layout);

        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).operation()).isEqualTo("TableReader");
        assertThat(nodes.get(0).children()).hasSize(1);
        assertThat(nodes.get(0).children().get(0).operation()).isEqualTo("Selection");
        assertThat(nodes.get(0).children().get(0).children().get(0).operation()).isEqualTo("TableFullScan");
        assertThat(nodes.get(0).children().get(0).children().get(0).rows()).isEqualTo(10000L);
    }

    @Test
    void mapTextPlanToNodes_indentBased() {
        String text = """
            - Output[user_id]
              - Aggregate(FINAL)
                - TableScan[hive:default.orders]
            """;
        var grammar = new TextPlanGrammar(
            "trino",
            line -> {
                int dashIdx = line.indexOf("- ");
                return dashIdx < 0 ? -1 : dashIdx / 2;
            },
            line -> {
                int dashIdx = line.indexOf("- ");
                if (dashIdx < 0) return null;
                String rest = line.substring(dashIdx + 2);
                int end = Math.min(Math.min(idxOr(rest, '['), idxOr(rest, ' ')), idxOr(rest, '('));
                return end < 0 ? rest.trim() : rest.substring(0, end);
            },
            line -> {
                int b = line.indexOf('[');
                int e = line.indexOf(']', b);
                if (b < 0 || e < 0) return Optional.empty();
                String inner = line.substring(b + 1, e);
                int colon = inner.indexOf(':');
                return Optional.of(colon >= 0 ? inner.substring(colon + 1) : inner);
            },
            line -> Optional.empty()
        );

        var nodes = provider.mapTextPlanToNodes(text, grammar);

        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).operation()).isEqualTo("Output");
        assertThat(nodes.get(0).children().get(0).operation()).isEqualTo("Aggregate");
        assertThat(nodes.get(0).children().get(0).children().get(0).operation()).isEqualTo("TableScan");
        assertThat(nodes.get(0).children().get(0).children().get(0).table()).isEqualTo("default.orders");
    }

    @Test
    void mapXmlPlanToNodes_extractsRelOpTree() {
        String xml = """
            <ShowPlanXML xmlns="http://schemas.microsoft.com/sqlserver/2004/07/showplan">
              <BatchSequence><Batch><Statements><StmtSimple>
                <QueryPlan>
                  <RelOp PhysicalOp="Hash Match" EstimateRows="100" EstimatedTotalSubtreeCost="0.5">
                    <RelOp PhysicalOp="Table Scan" EstimateRows="10000" EstimatedTotalSubtreeCost="0.4">
                      <Object Table="[orders]" />
                    </RelOp>
                  </RelOp>
                </QueryPlan>
              </StmtSimple></Statements></Batch></BatchSequence>
            </ShowPlanXML>
            """;
        var nodes = provider.mapXmlPlanToNodes(xml);

        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).operation()).isEqualTo("Hash Match");
        assertThat(nodes.get(0).children()).hasSize(1);
        assertThat(nodes.get(0).children().get(0).operation()).isEqualTo("Table Scan");
        assertThat(nodes.get(0).children().get(0).table()).isEqualTo("orders");
        assertThat(nodes.get(0).children().get(0).rows()).isEqualTo(10000L);
    }

    @Test
    void parseScanType_returnsOtherForUnknownToken() {
        var overrides = Map.of("scan", ScanType.FULL_SCAN, "seek", ScanType.REF);
        assertThat(provider.parseScanType("scan", overrides)).isEqualTo(ScanType.FULL_SCAN);
        assertThat(provider.parseScanType("Seek", overrides)).isEqualTo(ScanType.REF);
        assertThat(provider.parseScanType("hash_match", overrides)).isEqualTo(ScanType.OTHER);
        assertThat(provider.parseScanType(null, overrides)).isEqualTo(ScanType.OTHER);
    }

    private static Map<String, Object> row(Object... pairs) {
        var m = new LinkedHashMap<String, Object>();
        for (int i = 0; i < pairs.length; i += 2) m.put(String.valueOf(pairs[i]), pairs[i + 1]);
        return m;
    }

    private static int idxOr(String s, char c) {
        int i = s.indexOf(c);
        return i < 0 ? Integer.MAX_VALUE : i;
    }

    private static class TestProvider extends AbstractDiagnosticsProvider {
        TestProvider() { super(null); }
        @Override public Set<String> supportedDriverTypes() { return Set.of("test"); }
        @Override public Set<DiagnosticCapability> supportedCapabilities() { return Set.of(); }
        @Override public DiagnosticResult<ExplainPlan> explain(String s, ConnectionRecord c, String p, String d, String sc) { return DiagnosticResult.unsupported(""); }
        @Override public DiagnosticResult<List<IndexRecommendation>> indexHints(String s, ExplainPlan p, ConnectionRecord c, String pw) { return DiagnosticResult.unsupported(""); }
        @Override public DiagnosticResult<LockReport> lockInfo(ConnectionRecord c, String p, String d) { return DiagnosticResult.unsupported(""); }
        @Override public DiagnosticResult<PoolReport> poolStatus(ConnectionRecord c, String p) { return DiagnosticResult.unsupported(""); }
        @Override public DiagnosticResult<SpaceReport> tableSpaceInfo(ConnectionRecord c, String p, String d, List<String> t) { return DiagnosticResult.unsupported(""); }
        @Override public DiagnosticResult<TerminateSessionPreview> terminateSessionPreview(ConnectionRecord c, String p, String t, String d) { return DiagnosticResult.unsupported(""); }
        @Override public DiagnosticResult<TerminateSessionResult> terminateSession(ConnectionRecord c, String p, String t, String d) { return DiagnosticResult.unsupported(""); }
        @Override public DiagnosticResult<OptimizeTablePreview> optimizeTablePreview(ConnectionRecord c, String p, String t, String s, String d) { return DiagnosticResult.unsupported(""); }
        @Override public DiagnosticResult<OptimizeTableResult> optimizeTable(ConnectionRecord c, String p, String t, String s, String d) { return DiagnosticResult.unsupported(""); }
    }
}