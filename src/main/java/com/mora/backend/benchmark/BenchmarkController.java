package com.mora.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.mora.backend.model.dto.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController @RequestMapping("/api/admin/benchmarks") @RequiredArgsConstructor
public class BenchmarkController {
    private final BenchmarkService service;
    // Spring Boot 4 uses Jackson 3 on the wire; keep internal Jackson 2 trees internal.
    private Object wire(Object result) {
        return result instanceof JsonNode ? new com.fasterxml.jackson.databind.ObjectMapper().convertValue(result,Object.class) : result;
    }
    private ApiResponse<Object> ok(Object result) { return ApiResponse.builder().result(wire(result)).build(); }
    @GetMapping("/datasets") public Object datasets() { return ok(service.listDatasets()); }
    @PostMapping("/datasets") public Object create(@RequestBody String body) { return ok(service.create(service.parse(body))); }
    @GetMapping("/datasets/{id}") public Object dataset(@PathVariable long id) { return ok(service.definition(id)); }
    @GetMapping("/datasets/{id}/export") public Object exportDataset(@PathVariable long id) { return wire(service.definition(id)); }
    @PutMapping("/datasets/{id}/questions/{questionId}") public Object edit(@PathVariable long id,@PathVariable long questionId,@RequestBody String body) {
        service.editQuestion(id,questionId,service.parse(body)); return ok(service.definition(id));
    }
    @PostMapping("/datasets/{id}/questions/{questionId}/approval") public Object approve(@PathVariable long id,@PathVariable long questionId,@RequestBody String body) {
        service.approve(id,questionId,service.parse(body).path("approved").asBoolean()); return ok(service.definition(id));
    }
    @PostMapping(value="/datasets/{id}/documents/{code}",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object upload(@PathVariable long id,@PathVariable String code,@RequestParam MultipartFile file) throws Exception {
        service.upload(id,code,file); return ok(service.definition(id));
    }
    @GetMapping("/runs") public Object runs() { return ok(service.listRuns()); }
    @PostMapping("/datasets/{id}/runs") public Object start(@PathVariable long id,@RequestBody String body) { return ok(service.start(id,service.parse(body).path("smoke").asBoolean())); }
    @GetMapping("/runs/{id}") public Object run(@PathVariable long id) { return ok(service.run(id)); }
    @PostMapping("/runs/{id}/resume") public Object resume(@PathVariable long id) { service.resume(id,false); return ok(service.run(id)); }
    @PostMapping("/runs/{id}/retry-judge") public Object retry(@PathVariable long id) { service.resume(id,true); return ok(service.run(id)); }
    @GetMapping("/runs/{id}/export") public ResponseEntity<?> export(@PathVariable long id,@RequestParam(defaultValue="json") String format) {
        if (format.equals("csv")) return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=benchmark-"+id+".csv")
            .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body("\uFEFF"+service.csv(id));
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=benchmark-"+id+".json").body(service.run(id));
    }
    @ExceptionHandler({IllegalArgumentException.class,IllegalStateException.class,NoSuchElementException.class})
    public ResponseEntity<?> invalid(RuntimeException e) {
        return ResponseEntity.badRequest().body(ApiResponse.builder().code(400).message(e.getMessage()).build());
    }
}
