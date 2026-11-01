package com.mora.backend.benchmark;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
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
    @Test void blocksUndeclaredGroundTruthAndUnreadyDocuments() {
        question.put("approved",false); assertThrows(IllegalArgumentException.class,()->service.start(1,true));
        question.put("approved",true); documents.findById(1L).orElseThrow().setStatus(DocumentStatus.INDEXING);
        assertThrows(IllegalArgumentException.class,()->service.start(1,true));
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
        var run=service.start(1,true); awaitFinished(done);
        String frozen=run.getSnapshotJson(); question.put("referenceAnswer","EDITED");
        assertEquals(frozen,run.getSnapshotJson()); assertTrue(frozen.contains("SECRET_GROUND_TRUTH"));
        assertEquals("JUDGE_ERROR",stored.get(0).getStatus()); assertNotNull(stored.get(0).getTraceJson());
        var retried=new CountDownLatch(1);
        when(ai.call(eq("score"),any())).thenAnswer(call -> {retried.countDown();return json.readTree("{\"contextPrecision\":{\"value\":0,\"status\":\"ok\"},\"faithfulness\":{\"status\":\"not_applicable\"},\"answerRelevance\":{\"value\":1,\"status\":\"ok\"}}");});
        service.resume(1,true); awaitFinished(retried);
        verify(ai,times(1)).call(eq("execute"),any());
        assertEquals("COMPLETED",stored.get(0).getStatus());
        assertTrue(service.csv(1).contains("123"));
    }
    @Test void recoveryMarksActiveRunsInterrupted() {
        var r=new BenchmarkRun(); r.setStatus("RUNNING");
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
}
