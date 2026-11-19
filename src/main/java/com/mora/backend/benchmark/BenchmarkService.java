package com.mora.backend.benchmark;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.mora.backend.exception.AppException;
import com.mora.backend.exception.ErrorCode;
import com.mora.backend.model.entity.*;
import com.mora.backend.repository.*;
import com.mora.backend.service.UserService;
import com.mora.backend.service.impl.DocumentAsyncProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Service @RequiredArgsConstructor
public class BenchmarkService implements DisposableBean {
    private static final Set<String> DELETABLE_RUN_STATUSES = Set.of("COMPLETED", "COMPLETED_WITH_ERRORS", "INTERRUPTED");
    private final BenchmarkDatasetRepository datasets;
    private final BenchmarkQuestionRepository questions;
    private final BenchmarkRunRepository runs;
    private final BenchmarkSampleRepository samples;
    private final SpaceRepository spaces;
    private final DocumentRepository documents;
    private final DocumentPageRepository pages;
    private final DocumentAsyncProcessor processor;
    private final UserService users;
    private final BenchmarkAiClient ai;
    private final PlatformTransactionManager transactions;
    private final ObjectMapper json = new ObjectMapper();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private boolean busy;

    public JsonNode parse(String value) {
        try { return json.readTree(value); } catch (Exception e) { throw new IllegalArgumentException("Invalid benchmark JSON"); }
    }
    private static void require(boolean condition, String reason) {
        if (!condition) throw new IllegalArgumentException(reason);
    }
    private BenchmarkDataset dataset(long id) { return datasets.findById(id).orElseThrow(() -> new IllegalArgumentException("Dataset not found")); }
    public List<BenchmarkDataset> listDatasets() { return datasets.findAllByOrderByIdDesc(); }
    public List<BenchmarkRun> listRuns() { return runs.findAllByOrderByIdDesc(); }

    @Transactional
    public BenchmarkRun renameRun(long id, String name) {
        if (runs.updateName(id, runName(name)) == 0) {
            throw new AppException(ErrorCode.BENCHMARK_RUN_NOT_FOUND);
        }
        return runs.findById(id).orElseThrow(() -> new AppException(ErrorCode.BENCHMARK_RUN_NOT_FOUND));
    }

    private String runName(String name) {
        if (name == null || name.isBlank()) return null;
        String trimmed = name.strip();
        require(trimmed.length() <= 100, "Tên lượt chạy tối đa 100 ký tự");
        return trimmed;
    }

    public synchronized void deleteRun(long runId) {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var run = runs.findForDeletion(runId)
                    .orElseThrow(() -> new AppException(ErrorCode.BENCHMARK_RUN_NOT_FOUND));
            if (!isDeletable(run)) {
                throw new AppException(ErrorCode.BENCHMARK_RUN_NOT_DELETABLE);
            }
            samples.deleteSamplesForRun(runId);
            runs.delete(run);
        });
    }

    public synchronized void deleteRuns(List<Long> requestedIds) {
        if (requestedIds == null || requestedIds.isEmpty() || requestedIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new AppException(ErrorCode.INVALID_REQUEST_PARAMETER);
        }
        var ids = requestedIds.stream().distinct().sorted().toList();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var selected = runs.findAllForDeletion(ids);
            var missing = new TreeSet<>(ids);
            selected.forEach(run -> missing.remove(run.getId()));
            var ineligible = selected.stream().filter(run -> !isDeletable(run)).map(BenchmarkRun::getId).toList();
            if (!missing.isEmpty() || !ineligible.isEmpty()) {
                throw new BulkDeleteConflict(List.copyOf(missing), ineligible);
            }
            samples.deleteSamplesForRuns(ids);
            runs.deleteAllInBatch(selected);
        });
    }

    private boolean isDeletable(BenchmarkRun run) {
        return run.getStatus() != null && DELETABLE_RUN_STATUSES.contains(run.getStatus());
    }

    static final class BulkDeleteConflict extends RuntimeException {
        final List<Long> missingIds;
        final List<Long> ineligibleIds;

        BulkDeleteConflict(List<Long> missingIds, List<Long> ineligibleIds) {
            this.missingIds = missingIds;
            this.ineligibleIds = ineligibleIds;
        }
    }

    public ObjectNode definition(long id) {
        var d = dataset(id);
        var result = (ObjectNode)parse(d.getDefinitionJson());
        result.put("id",d.getId()); result.put("spaceId",d.getSpaceId());
        var qs = result.putArray("questions");
        for (var q : questions.findByDatasetIdOrderByIdAsc(id)) {
            var node = (ObjectNode)parse(q.getDefinitionJson()); node.put("id",q.getId()); qs.add(node);
        }
        for (var doc : result.withArray("documents")) {
            var entity = documents.findById(doc.path("documentId").asLong(-1));
            ((ObjectNode)doc).put("status",entity.map(x -> x.getStatus().name()).orElse("NOT_UPLOADED"));
            ((ObjectNode)doc).put("storageUrl",entity.map(Document::getStorageUrl).orElse(""));
        }
        return result;
    }

    static void validateQuestion(JsonNode q, JsonNode docs) {
        require(Set.of("DIRECT","SYNTHESIS","VISUAL").contains(q.path("type").asText()),"Invalid question type");
        for (String field : List.of("code","question","referenceAnswer","explanation"))
            require(!q.path(field).asText().isBlank(),"Missing question field: "+field);
        require(q.path("sources").isArray() && !q.path("sources").isEmpty(),"Ground truth sources required");
        Map<String,Integer> pageCounts = new HashMap<>();
        for (var d : docs) pageCounts.put(d.path("code").asText(), d.path("pageCount").asInt());
        for (var s : q.path("sources")) {
            int page = s.path("pageNumber").asInt();
            require(page > 0 && page <= pageCounts.getOrDefault(s.path("documentCode").asText(),0),"Ground truth page outside PDF");
        }
    }

    @Transactional
    public BenchmarkDataset create(JsonNode input) {
        require(!input.path("name").asText().isBlank(),"Dataset name required");
        require(input.path("documents").isArray() && input.path("documents").size() >= 3 && input.path("documents").size() <= 5,"Use 3–5 PDFs");
        var root = json.createObjectNode();
        root.put("name",input.path("name").asText()); root.put("description",input.path("description").asText());
        var docs = root.putArray("documents"); Set<String> codes = new HashSet<>();
        for (var d : input.path("documents")) {
            require(codes.add(d.path("code").asText()) && !d.path("code").asText().isBlank(),"Duplicate/empty document code");
            require(d.path("sha256").asText().matches("[a-fA-F0-9]{64}") && d.path("pageCount").asInt()>0,"PDF hash and pageCount required");
            var item = docs.addObject();
            for (String key : List.of("code","fileName","sha256","pageCount")) item.set(key,d.path(key));
            item.putNull("documentId");
        }
        var space = spaces.save(Space.builder().name("Benchmark: "+input.path("name").asText())
            .description("Dedicated benchmark corpus").user(users.getCurrentUser()).build());
        var entity = new BenchmarkDataset(); entity.setName(input.path("name").asText());
        entity.setSpaceId(space.getId()); entity.setDefinitionJson(root.toString()); entity.setUpdatedAt(Instant.now());
        entity = datasets.save(entity); codes.clear();
        require(input.path("questions").isArray() && input.path("questions").size()>=1,"Questions required");
        for (var q : input.path("questions")) {
            validateQuestion(q,docs); require(codes.add(q.path("code").asText()),"Duplicate question code");
            var clean = cleanQuestion(q); clean.put("approved",false);
            var row = new BenchmarkQuestion(); row.setDatasetId(entity.getId()); row.setCode(q.path("code").asText());
            row.setDefinitionJson(clean.toString()); questions.save(row);
        }
        return entity;
    }
    private ObjectNode cleanQuestion(JsonNode input) {
        var node = json.createObjectNode();
        for (String f : List.of("code","type","question","referenceAnswer","explanation","sources")) node.set(f,input.path(f));
        return node;
    }
    @Transactional
    public void editQuestion(long datasetId, long questionId, JsonNode input) {
        var d = dataset(datasetId); validateQuestion(input,parse(d.getDefinitionJson()).path("documents"));
        var q = questions.findById(questionId).orElseThrow(); require(q.getDatasetId()==datasetId,"Question outside dataset");
        require(q.getCode().equals(input.path("code").asText()),"Question code is immutable");
        var clean = cleanQuestion(input); clean.put("approved",false); q.setDefinitionJson(clean.toString()); questions.save(q);
        d.setUpdatedAt(Instant.now()); datasets.save(d);
    }
    @Transactional
    public void approve(long datasetId, long questionId, boolean approved) {
        var q = questions.findById(questionId).orElseThrow(); require(q.getDatasetId()==datasetId,"Question outside dataset");
        var value = (ObjectNode)parse(q.getDefinitionJson());
        validateQuestion(value,parse(dataset(datasetId).getDefinitionJson()).path("documents"));
        value.put("approved",approved); q.setDefinitionJson(value.toString()); questions.save(q);
    }

    @Transactional
    public void upload(long id, String code, MultipartFile file) throws Exception {
        var d = dataset(id); var root = (ObjectNode)parse(d.getDefinitionJson());
        ObjectNode target = null;
        for (var doc : root.path("documents")) if (code.equals(doc.path("code").asText())) target=(ObjectNode)doc;
        require(target!=null,"Unknown document code"); require(target.path("documentId").isNull(),"Document already uploaded; import a new dataset to replace corpus");
        byte[] bytes=file.getBytes();
        require(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equalsIgnoreCase(target.path("sha256").asText()),"PDF hash does not match dataset");
        try (var pdf = org.apache.pdfbox.Loader.loadPDF(bytes)) { require(pdf.getNumberOfPages()==target.path("pageCount").asInt(),"Page count mismatch"); }
        var user = users.getCurrentUser();
        var doc = documents.save(Document.builder().name(target.path("fileName").asText()).storageUrl("")
            .space(spaces.findById(d.getSpaceId()).orElseThrow()).uploadedBy(user).fileSize((long)bytes.length)
            .contentType("application/pdf").status(DocumentStatus.UPLOADING).build());
        target.put("documentId",doc.getId()); d.setDefinitionJson(root.toString()); datasets.save(d);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { processor.processBenchmarkDocumentAsync(doc.getId(),d.getSpaceId(),bytes,doc.getName()); }
        });
    }

    private ObjectNode snapshot(long id, boolean smoke) {
        var root = definition(id); var qs=root.withArray("questions");
        require(qs.size()>0,"No questions");
        if (!smoke) {
            require(qs.size()>=30 && qs.size()<=40,"Official run requires 30–40 questions");
            for (String type : List.of("DIRECT","SYNTHESIS","VISUAL"))
                require(java.util.stream.StreamSupport.stream(qs.spliterator(),false).anyMatch(q -> type.equals(q.path("type").asText())),"Missing question type "+type);
        }
        for (var q : qs) require(q.path("approved").asBoolean(),"Approve every ground truth question first");
        var context=root.putArray("rawContext");
        for (var doc : root.path("documents")) {
            require("READY".equals(doc.path("status").asText()),"All PDFs must be READY");
            long docId=doc.path("documentId").asLong();
            var pp=pages.findByDocumentIdOrderByPageNumberAsc(docId);
            require(!pp.isEmpty(),"Document has no parsed pages");
            for (var p : pp) context.addObject().put("documentId",docId).put("documentName",doc.path("fileName").asText())
                .put("pageNumber",p.getPageNumber()).put("text",p.getText());
        }
        if (smoke) { var first=qs.get(0).deepCopy(); qs.removeAll(); qs.add(first); }
        var corpusBody=json.createObjectNode(); corpusBody.set("spaceId",root.path("spaceId"));
        var ids=corpusBody.putArray("documentIds"); for(var doc:root.path("documents")) ids.add(doc.path("documentId"));
        corpusBody.set("context", context);
        root.set("corpus",ai.call("prepare",corpusBody));
        return root;
    }
    private String commit(Path path) {
        try {
            String absolute=path.toAbsolutePath().normalize().toString().replace('\\','/');
            var p = new ProcessBuilder("git","-c","safe.directory="+absolute,"-C",absolute,"rev-parse","HEAD").redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if (!p.waitFor(5,TimeUnit.SECONDS)) { p.destroyForcibly(); return "unknown"; }
            return p.exitValue()==0 ? new String(p.getInputStream().readAllBytes()).trim() : "unknown";
        } catch (Exception e) { return "unknown"; }
    }
    private ObjectNode runConfig() {
        var config=json.createObjectNode(); config.set("ai",ai.call("config",null));
        config.put("backendCommit",commit(Path.of("."))); config.put("frontendCommit",commit(Path.of("../mora-frontend")));
        return config;
    }
    public synchronized BenchmarkRun start(long datasetId, boolean smoke, String name) {
        String normalizedName = runName(name);
        require(!busy && runs.findByStatus("RUNNING").isEmpty(),"A benchmark is already running");
        var snap=snapshot(datasetId,smoke); var config=runConfig();
        var r=new TransactionTemplate(transactions).execute(tx -> {
            var row=new BenchmarkRun(); row.setDatasetId(datasetId); row.setStatus("PREPARING"); row.setSmoke(smoke); row.setCreatedAt(Instant.now());
            row.setName(normalizedName);
            row.setSnapshotJson(snap.toString()); row.setConfigJson(config.toString()); row=runs.save(row);
            for (var q : snap.path("questions")) for (int repetition=1;repetition<=(smoke?1:3);repetition++) {
                var s=new BenchmarkSample(); s.setRunId(row.getId()); s.setQuestionCode(q.path("code").asText());
                s.setQuestionType(q.path("type").asText()); s.setRepetition(repetition); s.setStatus("PENDING"); samples.save(s);
            }
            return row;
        });
        launch(r,false); return r;
    }
    public synchronized void resume(long runId, boolean judgeOnly) {
        require(!busy,"A benchmark is already running"); var r=runs.findById(runId).orElseThrow();
        require(!"RUNNING".equals(r.getStatus()),"Run already active");
        require(parse(r.getConfigJson()).equals(runConfig()),"Configuration/commits changed; create a new run");
        // Ensure the frozen corpus still exists before using its vector IDs again.
        for (var d : parse(r.getSnapshotJson()).path("documents")) {
            var doc=documents.findById(d.path("documentId").asLong()).orElseThrow();
            require(doc.getStatus()==DocumentStatus.READY,"Corpus no longer READY");
        }
        launch(r,judgeOnly);
    }
    private void launch(BenchmarkRun r, boolean judgeOnly) {
        require(updateRunStatus(r, "RUNNING") != 0, "Không tìm thấy lượt chạy Benchmark");
        busy=true;
        worker.submit(() -> {
            try { process(r,judgeOnly); }
            catch (Exception e) { updateRunStatus(r, "INTERRUPTED"); }
            finally { synchronized(this) { busy=false; } }
        });
    }

    private int updateRunStatus(BenchmarkRun run, String status) {
        int updated = runs.updateStatus(run.getId(), status);
        run.setStatus(status);
        return updated;
    }
    private void process(BenchmarkRun run, boolean judgeOnly) {
        var snap=parse(run.getSnapshotJson()); var config=parse(run.getConfigJson()).path("ai");
        for (var s : samples.findByRunIdOrderByIdAsc(run.getId())) {
            if ("COMPLETED".equals(s.getStatus()) || (judgeOnly && !"JUDGE_ERROR".equals(s.getStatus()))) continue;
            if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("Worker interrupted");
            JsonNode q=null;
            for (var candidate : snap.path("questions")) if (candidate.path("code").asText().equals(s.getQuestionCode())) q=candidate;
            if (q==null) throw new IllegalStateException("Snapshot question missing");
            s.setError(null);
            if (s.getTraceJson()==null) {
                s.setStatus("GENERATING"); samples.save(s);
                var body=json.createObjectNode(); body.put("question",q.path("question").asText()); body.set("spaceId",snap.path("spaceId"));
                var ids=body.putArray("documentIds"); for (var d : snap.path("documents")) ids.add(d.path("documentId"));
                body.set("context",snap.path("rawContext")); body.set("config",config);
                body.set("corpusFingerprint",snap.path("corpus").path("fingerprint"));
                try {
                    var trace=ai.call("execute",body); s.setTraceJson(trace.toString());
                    if (!trace.path("errors").isEmpty()) { s.setStatus("GENERATION_ERROR"); s.setError("Pipeline provider errors; inspect trace"); samples.save(s); continue; }
                    s.setStatus("GENERATED"); samples.save(s);
                } catch (Exception e) { s.setStatus("GENERATION_ERROR"); s.setError("AI pipeline unavailable or timed out"); samples.save(s); continue; }
            }
            if ("GENERATION_ERROR".equals(s.getStatus())) continue;
            s.setStatus("SCORING"); samples.save(s);
            try {
                var trace=parse(s.getTraceJson()); var body=json.createObjectNode(); body.put("question",q.path("question").asText());
                body.set("referenceAnswer",q.path("referenceAnswer")); body.set("trace",trace); body.set("config",config);
                var metrics=(ObjectNode)ai.call("score",body);
                metrics.set("citationAccuracy",json.valueToTree(BenchmarkMath.citationMetric(
                        trace.path("response").path("citations"),q.path("sources"),snap.path("documents"))));
                metrics.set("latency",json.createObjectNode().set("value",trace.path("pipeline").path("timings").path("totalMs")));
                ((ObjectNode)metrics.get("latency")).put("status","ok");
                ((ObjectNode)metrics.get("latency")).set("details",json.valueToTree(
                        BenchmarkMath.latencyDetails(trace.path("pipeline").path("timings"))));
                boolean errors=false; for (var m : metrics) if ("error".equals(m.path("status").asText())) errors=true;
                s.setMetricsJson(metrics.toString()); s.setStatus(errors?"JUDGE_ERROR":"COMPLETED");
                if (errors) s.setError("Some judge metrics failed");
            } catch (Exception e) { s.setStatus("JUDGE_ERROR"); s.setError("Judge unavailable or timed out"); }
            samples.save(s);
        }
        boolean errors=samples.findByRunIdOrderByIdAsc(run.getId()).stream().anyMatch(s -> !"COMPLETED".equals(s.getStatus()));
        updateRunStatus(run, errors ? "COMPLETED_WITH_ERRORS" : "COMPLETED");
    }
    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        for (String status : List.of("RUNNING","PREPARING")) for (var r : runs.findByStatus(status)) updateRunStatus(r, "INTERRUPTED");
    }
    public Map<String,Object> run(long id) {
        var r=runs.findById(id).orElseThrow(); var rows=samples.findByRunIdOrderByIdAsc(id);
        Map<String,Object> result=new LinkedHashMap<>(); result.put("run",r); result.put("samples",rows);
        result.put("summary",summary(rows)); return result;
    }
    public Map<String,Object> summary(List<BenchmarkSample> rows) {
        Map<String,Object> groups=new LinkedHashMap<>(); groups.put("ALL",groupSummary(rows));
        for (String type : List.of("DIRECT","SYNTHESIS","VISUAL")) groups.put(type,groupSummary(rows.stream().filter(s -> type.equals(s.getQuestionType())).toList()));
        return groups;
    }
    private Map<String,Object> groupSummary(List<BenchmarkSample> rows) {
        Map<String,Object> result=new LinkedHashMap<>(); result.put("total",rows.size());
        result.put("completed",rows.stream().filter(s -> "COMPLETED".equals(s.getStatus())).count());
        result.put("errors",rows.stream().filter(s -> s.getStatus().endsWith("ERROR")).count());
        result.put("errorRate",rows.isEmpty()?null:rows.stream().filter(s -> s.getStatus().endsWith("ERROR")).count()/(double)rows.size());
        long traced=rows.stream().filter(s -> s.getTraceJson()!=null).count(); result.put("traced",traced);
        long fallback=0, reranked=0;
        for (var s : rows) if (s.getTraceJson()!=null) {
            var t=parse(s.getTraceJson()); if(t.path("pipeline").path("fallback").asBoolean()) fallback++;
            if("applied".equals(t.path("retrieval").path("rerank").asText())) reranked++;
        }
        result.put("fallbackCount",fallback); result.put("rerankedCount",reranked);
        result.put("fallbackRate",traced==0?null:fallback/(double)traced); result.put("rerankRate",traced==0?null:reranked/(double)traced);
        for (String name : List.of("contextPrecision","faithfulness","answerRelevance","citationAccuracy","latency")) {
            List<Double> values=new ArrayList<>(); int errors=0,missing=0,na=0;
            for (var s : rows) {
                var metric=s.getMetricsJson()==null ? json.createObjectNode() : parse(s.getMetricsJson()).path(name);
                if (name.equals("latency") && s.getTraceJson()!=null) {
                    var value=parse(s.getTraceJson()).path("pipeline").path("timings").path("totalMs");
                    if(value.isNumber()) { values.add(value.asDouble()); continue; }
                }
                if ("ok".equals(metric.path("status").asText()) && metric.path("value").isNumber()) values.add(metric.path("value").asDouble());
                else if ("not_applicable".equals(metric.path("status").asText())) na++;
                else if ("error".equals(metric.path("status").asText()) || s.getStatus().endsWith("ERROR")) errors++;
                else missing++;
            }
            Map<String,Object> m=new LinkedHashMap<>(); m.put("valid",values.size()); m.put("errors",errors); m.put("missing",missing); m.put("notApplicable",na);
            m.put("mean",values.isEmpty()?null:values.stream().mapToDouble(Double::doubleValue).average().orElseThrow());
            m.put("median",values.isEmpty()?null:BenchmarkMath.percentile(values,.5));
            m.put("p95",values.isEmpty()?null:BenchmarkMath.percentile(values,.95));
            result.put(name,m);
        }
        return result;
    }
    public String csv(long id) {
        StringBuilder out=new StringBuilder("questionCode,type,repetition,status,contextPrecision,faithfulness,answerRelevance,citationAccuracy,latencyMs,error\r\n");
        for (var s : samples.findByRunIdOrderByIdAsc(id)) {
            var m=s.getMetricsJson()==null?json.createObjectNode():parse(s.getMetricsJson());
            List<String> cells=new ArrayList<>(List.of(s.getQuestionCode(),s.getQuestionType(),String.valueOf(s.getRepetition()),s.getStatus()));
            for (String metric : List.of("contextPrecision","faithfulness","answerRelevance","citationAccuracy","latency")) cells.add(m.path(metric).path("value").asText(""));
            cells.add(s.getError()==null?"":s.getError());
            out.append(cells.stream().map(BenchmarkService::csvCell).collect(java.util.stream.Collectors.joining(","))).append("\r\n");
        }
        return out.toString();
    }
    private static String csvCell(String value) {
        if (value.matches("^[=+@-].*")) value="'"+value;
        return "\""+value.replace("\"","\"\"")+"\"";
    }
    @Override public void destroy() { worker.shutdownNow(); }
}
