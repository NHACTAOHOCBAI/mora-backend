package com.mora.backend.benchmark;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.mora.backend.exception.AppException;
import com.mora.backend.exception.ErrorCode;
import com.mora.backend.model.entity.*;
import com.mora.backend.repository.*;
import com.mora.backend.service.UserService;
import com.mora.backend.service.impl.DocumentAsyncProcessor;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class BenchmarkServiceTest {
    final ObjectMapper json=new ObjectMapper();
    BenchmarkDatasetRepository datasets;
    BenchmarkQuestionRepository questions;
    BenchmarkRunRepository runs;
    BenchmarkSampleRepository samples;
    DocumentRepository documents;
    DocumentPageRepository pages;
    BenchmarkAiClient ai;
    BenchmarkService service;
    List<BenchmarkSample> stored;
    BenchmarkRun current;
    ObjectNode question;
    @BeforeEach void setup() throws Exception {
        datasets=mock(BenchmarkDatasetRepository.class); questions=mock(BenchmarkQuestionRepository.class);
        runs=mock(BenchmarkRunRepository.class); samples=mock(BenchmarkSampleRepository.class);
        documents=mock(DocumentRepository.class); pages=mock(DocumentPageRepository.class); ai=mock(BenchmarkAiClient.class);
        var tx=mock(PlatformTransactionManager.class); when(tx.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        service=new BenchmarkService(datasets,questions,runs,samples,mock(SpaceRepository.class),documents,pages,mock(DocumentAsyncProcessor.class),mock(UserService.class),ai,tx);
        stored=new CopyOnWriteArrayList<>();
        when(samples.save(any())).thenAnswer(call -> { BenchmarkSample s=call.getArgument(0); if(s.getId()==null) {s.setId((long)stored.size()+1);stored.add(s);} return s; });
        when(samples.findByRunIdOrderByIdAsc(anyLong())).thenAnswer(call -> new ArrayList<>(stored));
        when(runs.save(any())).thenAnswer(call -> {current=call.getArgument(0); current.setId(1L); return current;});
        when(runs.findById(1L)).thenAnswer(call -> Optional.ofNullable(current));
        when(runs.findByStatus(anyString())).thenReturn(List.of());
        when(runs.updateStatus(anyLong(), anyString())).thenReturn(1);
        var dataset=new BenchmarkDataset(); dataset.setId(1L); dataset.setSpaceId(5L); dataset.setName("fixture");
        dataset.setDefinitionJson("{\"name\":\"fixture\",\"documents\":[{\"code\":\"D1\",\"documentId\":1,\"fileName\":\"one.pdf\"},{\"code\":\"D2\",\"documentId\":2},{\"code\":\"D3\",\"documentId\":3}]}");
        when(datasets.findById(1L)).thenReturn(Optional.of(dataset));
        question=(ObjectNode)json.readTree("{\"code\":\"Q1\",\"type\":\"DIRECT\",\"question\":\"question\",\"referenceAnswer\":\"SECRET_GROUND_TRUTH\",\"explanation\":\"e\",\"sources\":[{\"documentCode\":\"D1\",\"pageNumber\":1}],\"approved\":true}");
        when(questions.findByDatasetIdOrderByIdAsc(1L)).thenAnswer(call -> {var q=new BenchmarkQuestion();q.setId(1L);q.setDefinitionJson(question.toString());return List.of(q);});
        for(long id=1;id<=3;id++) {
            var doc=new Document(); doc.setStatus(DocumentStatus.READY); doc.setStorageUrl("https://example.test/source.pdf");
            when(documents.findById(id)).thenReturn(Optional.of(doc));
            var p=new DocumentPage();p.setPageNumber(1);p.setText("SOURCE_TEXT");
            when(pages.findByDocumentIdOrderByPageNumberAsc(id)).thenReturn(List.of(p));
        }
        when(ai.call(eq("config"),isNull())).thenReturn(json.readTree("{\"chatModel\":\"fixture\"}"));
        when(ai.call(eq("prepare"),any())).thenAnswer(call -> {
            JsonNode body = call.getArgument(1);
            assertEquals(3, body.path("context").size());
            assertEquals("SOURCE_TEXT", body.path("context").get(0).path("text").asText());
            assertFalse(body.toString().contains("SECRET_GROUND_TRUTH"));
            return json.readTree("{\"fingerprint\":\"fixture\"}");
        });
    }
    @AfterEach void close() {service.destroy();}

    @Test void deletesOnlySamplesBelongingToTheSelectedRunBeforeDeletingIt() {
        var run = new BenchmarkRun();
        run.setId(42L);
        run.setStatus("COMPLETED_WITH_ERRORS");
        when(runs.findForDeletion(42L)).thenReturn(Optional.of(run));

        service.deleteRun(42L);

        var order = inOrder(samples, runs);
        order.verify(runs).findForDeletion(42L);
        order.verify(samples).deleteSamplesForRun(42L);
        order.verify(runs).delete(run);
    }

    @Test void rejectsDeletionOfActiveAndMissingRuns() {
        var active = new BenchmarkRun();
        active.setStatus("RUNNING");
        when(runs.findForDeletion(42L)).thenReturn(Optional.of(active));
        when(runs.findForDeletion(99L)).thenReturn(Optional.empty());

        assertEquals(ErrorCode.BENCHMARK_RUN_NOT_DELETABLE, assertThrows(AppException.class, () -> service.deleteRun(42L)).getErrorCode());
        assertEquals(ErrorCode.BENCHMARK_RUN_NOT_FOUND, assertThrows(AppException.class, () -> service.deleteRun(99L)).getErrorCode());
        verify(samples, never()).deleteSamplesForRun(anyLong());
        verify(runs, never()).delete(any());
    }

    @Test void bulkDeleteValidatesEveryRunBeforeDeletingAnything() {
        var completed = new BenchmarkRun();
        completed.setId(1L);
        completed.setStatus("COMPLETED");
        var active = new BenchmarkRun();
        active.setId(2L);
        active.setStatus("RUNNING");
        when(runs.findAllForDeletion(List.of(1L, 2L, 3L))).thenReturn(List.of(completed, active));

        var error = assertThrows(BenchmarkService.BulkDeleteConflict.class,
                () -> service.deleteRuns(List.of(3L, 2L, 1L, 1L)));
        assertEquals(List.of(3L), error.missingIds);
        assertEquals(List.of(2L), error.ineligibleIds);
        verify(samples, never()).deleteSamplesForRuns(anyList());
        verify(runs, never()).deleteAllInBatch(anyList());

        when(runs.findAllForDeletion(List.of(1L, 2L))).thenReturn(List.of(completed, active));
        active.setStatus("INTERRUPTED");
        service.deleteRuns(List.of(2L, 1L));
        var order = inOrder(samples, runs);
        order.verify(runs).findAllForDeletion(List.of(1L, 2L));
        order.verify(samples).deleteSamplesForRuns(List.of(1L, 2L));
        order.verify(runs).deleteAllInBatch(List.of(completed, active));
    }
    @Test void blocksUndeclaredGroundTruthAndUnreadyDocuments() {
        question.put("approved",false); assertThrows(IllegalArgumentException.class,()->service.start(1,true,null));
        question.put("approved",true); documents.findById(1L).orElseThrow().setStatus(DocumentStatus.INDEXING);
        assertThrows(IllegalArgumentException.class,()->service.start(1,true,null));
        verify(ai,never()).call(eq("execute"),any());
    }
    private void awaitFinished(CountDownLatch done) throws Exception {
        assertTrue(done.await(10,TimeUnit.SECONDS));
        // The worker persists terminal status immediately after the last sample.
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(current.getStatus().equals("RUNNING") && System.nanoTime()<end) Thread.sleep(5);
        assertNotEquals("RUNNING",current.getStatus());
    }
    @Test void immutableSnapshotAndJudgeRetryNeverRegenerate() throws Exception {
        var trace=json.readTree("{\"errors\":[],\"response\":{\"answer\":\"answer\",\"citations\":[]},\"pipeline\":{\"timings\":{\"totalMs\":123},\"contexts\":[]}}");
        when(ai.call(eq("execute"),any())).thenAnswer(call -> {
            JsonNode body=call.getArgument(1); assertFalse(body.has("referenceAnswer")); assertFalse(body.toString().contains("SECRET_GROUND_TRUTH"));
            assertEquals(3,body.path("documentIds").size()); return trace;
        });
        var done=new CountDownLatch(1);
        when(ai.call(eq("score"),any())).thenAnswer(call -> {done.countDown(); throw new IllegalStateException("judge offline");});
        var run=service.start(1,true,"  Rerank top 8  "); awaitFinished(done);
        assertEquals("Rerank top 8",run.getName());
        String frozen=run.getSnapshotJson(); question.put("referenceAnswer","EDITED");
        assertEquals(frozen,run.getSnapshotJson()); assertTrue(frozen.contains("SECRET_GROUND_TRUTH"));
        assertEquals("JUDGE_ERROR",stored.get(0).getStatus()); assertNotNull(stored.get(0).getTraceJson());
        var retried=new CountDownLatch(1);
        when(ai.call(eq("score"),any())).thenAnswer(call -> {retried.countDown();return json.readTree("{\"contextPrecision\":{\"value\":0,\"status\":\"ok\"},\"faithfulness\":{\"status\":\"not_applicable\"},\"answerRelevance\":{\"value\":1,\"status\":\"ok\"}}");});
        service.resume(1,true); awaitFinished(retried);
        verify(ai,times(1)).call(eq("execute"),any());
        assertEquals("COMPLETED",stored.get(0).getStatus());
        assertTrue(service.csv(1).contains("123"));
        var metrics=json.readTree(stored.get(0).getMetricsJson());
        assertEquals(1,metrics.path("citationAccuracy").path("details").path("version").asInt());
        assertEquals(0,metrics.path("citationAccuracy").path("details").path("predictedCount").asInt());
        assertEquals(123.,metrics.path("latency").path("details").path("totalMs").asDouble());
    }
    @Test void recoveryMarksActiveRunsInterrupted() {
        var r=new BenchmarkRun(); r.setId(1L); r.setStatus("RUNNING");
        when(runs.findByStatus("RUNNING")).thenReturn(List.of(r)); service.recover(); assertEquals("INTERRUPTED",r.getStatus());
    }
    @Test void summariesRetainMissingErrorsAndNotApplicable() throws Exception {
        var s=new BenchmarkSample();s.setQuestionType("DIRECT");s.setStatus("JUDGE_ERROR");
        s.setMetricsJson("{\"faithfulness\":{\"status\":\"not_applicable\"},\"answerRelevance\":{\"status\":\"error\"}}");
        var result=json.valueToTree(service.summary(List.of(s)));
        assertEquals(1,result.path("ALL").path("faithfulness").path("notApplicable").asInt());
        assertEquals(1,result.path("ALL").path("answerRelevance").path("errors").asInt());
        assertTrue(result.path("ALL").path("faithfulness").path("mean").isNull());
    }
    @Test void allMetricsIncludeMedianAndP95FromValidScoresOnly() {
        var names=List.of("contextPrecision","faithfulness","answerRelevance","citationAccuracy","latency");
        List<BenchmarkSample> rows=new ArrayList<>();
        for(double value:List.of(.2,.4,.6,1.)) {
            var s=new BenchmarkSample();s.setQuestionType("DIRECT");s.setStatus("COMPLETED");
            var metrics=json.createObjectNode();
            for(String name:names) metrics.putObject(name).put("value",value).put("status","ok");
            s.setMetricsJson(metrics.toString());rows.add(s);
        }
        for(String status:List.of("error","not_applicable")) {
            var s=new BenchmarkSample();s.setQuestionType("DIRECT");s.setStatus("JUDGE_ERROR");
            var metrics=json.createObjectNode();
            for(String name:names) metrics.putObject(name).put("value",999).put("status",status);
            s.setMetricsJson(metrics.toString());rows.add(s);
        }
        var result=json.valueToTree(service.summary(rows));
        for(String group:List.of("ALL","DIRECT")) for(String name:names) {
            var metric=result.path(group).path(name);
            assertEquals(4,metric.path("valid").asInt());
            assertEquals(.55,metric.path("mean").asDouble(),1e-9);
            assertEquals(.5,metric.path("median").asDouble(),1e-9);
            assertEquals(.94,metric.path("p95").asDouble(),1e-9);
            assertEquals(1,metric.path("errors").asInt());
            assertEquals(1,metric.path("notApplicable").asInt());
        }
        for(String name:names) {
            assertTrue(result.path("VISUAL").path(name).path("median").isNull());
            assertTrue(result.path("VISUAL").path(name).path("p95").isNull());
        }
    }
    @Test void singleValidScoreHasIdenticalMeanMedianAndP95() {
        var sample=new BenchmarkSample();sample.setQuestionType("VISUAL");sample.setStatus("COMPLETED");
        sample.setMetricsJson("{\"faithfulness\":{\"value\":0.8,\"status\":\"ok\"}}");
        var metric=json.valueToTree(service.summary(List.of(sample))).path("ALL").path("faithfulness");
        assertEquals(.8,metric.path("mean").asDouble());
        assertEquals(.8,metric.path("median").asDouble());
        assertEquals(.8,metric.path("p95").asDouble());
    }
}
