package com.mora.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class BenchmarkAiClient {
    @Value("${ai-service.url:http://localhost:8000}") private String baseUrl;
    private final ObjectMapper json = new ObjectMapper();
    private final RestTemplate http;
    public BenchmarkAiClient() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(600000);
        http = new RestTemplate(factory);
    }
    public JsonNode call(String action, Object body) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            var response = http.exchange(baseUrl + "/api/internal/benchmarks/" + action,
                    body == null ? HttpMethod.GET : HttpMethod.POST,
                    new HttpEntity<>(body == null ? null : json.writeValueAsString(body), headers), String.class);
            JsonNode result = json.readTree(response.getBody());
            return result;
        } catch (Exception e) {
            // Provider bodies and credentials must never reach persisted benchmark errors.
            throw new IllegalStateException("AI benchmark request failed: " + e.getClass().getSimpleName());
        }
    }
    public JsonNode parsePdf(byte[] content, String filename) {
        var headers=new HttpHeaders(); headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        var body=new org.springframework.util.LinkedMultiValueMap<String,Object>();
        body.add("file",new org.springframework.core.io.ByteArrayResource(content) {
            @Override public String getFilename() { return filename; }
        });
        try {
            var response=http.postForObject(baseUrl+"/api/internal/benchmarks/parse",new HttpEntity<>(body,headers),String.class);
            JsonNode result = json.readTree(response);
            return result;
        } catch(Exception e) { throw new IllegalStateException("Benchmark PDF parsing failed: "+e.getClass().getSimpleName()); }
    }
}
