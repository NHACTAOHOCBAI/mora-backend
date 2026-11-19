package com.mora.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class BenchmarkMathTest {
    final ObjectMapper json=new ObjectMapper();
    @Test void citationsRequireDocumentAndPageAndDeduplicate() throws Exception {
        var docs=json.readTree("[{\"code\":\"D1\",\"documentId\":1},{\"code\":\"D2\",\"documentId\":2}]");
        var sources=json.readTree("[{\"documentCode\":\"D1\",\"pageNumber\":3}]");
        var citations=json.readTree("[{\"documentId\":1,\"pageNumber\":3},{\"documentId\":1,\"pageNumber\":3},{\"documentId\":2,\"pageNumber\":3},{\"documentId\":1,\"pageNumber\":4}]");
        assertEquals(1./3,BenchmarkMath.citationAccuracy(citations,sources,docs),1e-9);
        assertEquals(0,BenchmarkMath.citationAccuracy(json.readTree("[]"),sources,docs));
        assertEquals(0,BenchmarkMath.citationAccuracy(json.readTree("[{\"pageNumber\":3}]"),sources,docs));
    }
    @Test void percentileInterpolates() {
        assertEquals(2.5,BenchmarkMath.percentile(List.of(4.,1.,3.,2.),.5));
        assertEquals(3.85,BenchmarkMath.percentile(List.of(4.,1.,3.,2.),.95),1e-9);
    }
    @Test void citationDetailsExplainTheSameScoreAndRetainUnknownSources() throws Exception {
        var docs=json.readTree("[{\"code\":\"D1\",\"documentId\":1}]");
        var sources=json.readTree("[{\"documentCode\":\"D1\",\"pageNumber\":3},{\"documentCode\":\"D1\",\"pageNumber\":3}]");
        var citations=json.readTree("[{\"documentId\":1,\"pageNumber\":3},{\"documentId\":1,\"pageNumber\":3},{\"pageNumber\":3}]");
        var metric=json.valueToTree(BenchmarkMath.citationMetric(citations,sources,docs));
        var details=metric.path("details");
        assertEquals(.5,metric.path("value").asDouble());
        assertEquals(2,details.path("predictedCount").asInt());
        assertEquals(1,details.path("matchedCount").asInt());
        assertEquals(1,details.path("expectedSources").size());
        assertEquals(2,details.path("rows").size());
        assertTrue(details.path("rows").get(1).path("documentCode").asText().startsWith("unknown-"));
        assertFalse(details.path("rows").get(1).path("correct").asBoolean());
        assertTrue(details.path("complete").asBoolean());
    }
    @Test void latencyComponentsExplainTotalAndNeverInventMissingMeasurements() throws Exception {
        var details=BenchmarkMath.latencyDetails(json.readTree("{\"routerMs\":10,\"retrievalMs\":20,\"synthesisMs\":40,\"evaluatorMs\":25,\"totalMs\":100}"));
        assertEquals(true,details.get("complete"));
        assertEquals(95.,details.get("componentSumMs"));
        assertEquals(5.,details.get("overheadMs"));
        var partial=BenchmarkMath.latencyDetails(json.readTree("{\"totalMs\":100}"));
        assertEquals(false,partial.get("complete"));
        assertFalse(partial.containsKey("overheadMs"));
        assertFalse(partial.containsKey("routerMs"));
    }
    @Test void groundTruthMustReferenceValidPdfPage() throws Exception {
        var q=json.readTree("{\"code\":\"Q1\",\"type\":\"VISUAL\",\"question\":\"q\",\"referenceAnswer\":\"a\",\"explanation\":\"e\",\"sources\":[{\"documentCode\":\"D1\",\"pageNumber\":11}]}");
        var docs=json.readTree("[{\"code\":\"D1\",\"pageCount\":10}]");
        assertThrows(IllegalArgumentException.class,()->BenchmarkService.validateQuestion(q,docs));
        ((com.fasterxml.jackson.databind.node.ObjectNode)q.path("sources").get(0)).put("pageNumber",10);
        assertDoesNotThrow(()->BenchmarkService.validateQuestion(q,docs));
    }
}
