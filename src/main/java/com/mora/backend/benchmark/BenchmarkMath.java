package com.mora.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

public final class BenchmarkMath {
    private BenchmarkMath() {}
    public static double citationAccuracy(JsonNode citations, JsonNode sources, JsonNode documents) {
        return ((Number)citationMetric(citations, sources, documents).get("value")).doubleValue();
    }
    public static Map<String,Object> citationMetric(JsonNode citations, JsonNode sources, JsonNode documents) {
        Map<Long,String> codes = new HashMap<>();
        for (var d : documents) codes.put(d.path("documentId").asLong(), d.path("code").asText());
        Map<String,Map<String,Object>> expected = new TreeMap<>(), actual = new TreeMap<>();
        for (var s : sources) {
            String code = s.path("documentCode").asText();
            int page = s.path("pageNumber").asInt();
            expected.put(code+":"+page, Map.of("documentCode",code,"pageNumber",page));
        }
        for (var c : citations) {
            long id = c.path("documentId").asLong(-1);
            // Unknown or missing document IDs remain incorrect, even if the page number matches.
            String code = codes.getOrDefault(id,"unknown-"+id);
            int page = c.path("pageNumber").asInt();
            String key = code+":"+page;
            actual.put(key, Map.of("documentId",id,"documentCode",code,"pageNumber",page,
                    "correct",expected.containsKey(key)));
        }
        long matched = actual.keySet().stream().filter(expected::containsKey).count();
        return Map.of("value", actual.isEmpty() ? 0 : matched / (double)actual.size(), "status","ok",
                "details",Map.of("version",1,"complete",true,"expectedSources",expected.values(),
                        "rows",actual.values(),"matchedCount",matched,"predictedCount",actual.size()));
    }
    public static Map<String,Object> latencyDetails(JsonNode timings) {
        Map<String,Object> details = new LinkedHashMap<>();
        details.put("version",1);
        boolean complete = timings.path("totalMs").isNumber();
        double sum = 0;
        for (String key : List.of("routerMs","retrievalMs","synthesisMs","evaluatorMs")) {
            if (timings.path(key).isNumber()) {
                double value = timings.path(key).asDouble();
                details.put(key,value); sum += value;
            } else complete = false;
        }
        if (timings.path("totalMs").isNumber()) details.put("totalMs",timings.path("totalMs").asDouble());
        if (complete) {
            details.put("componentSumMs",sum);
            details.put("overheadMs",timings.path("totalMs").asDouble()-sum);
        }
        details.put("complete",complete);
        return details;
    }
    public static double percentile(List<Double> values, double fraction) {
        if (values.isEmpty()) return 0;
        var sorted = values.stream().sorted().toList();
        double position = (sorted.size()-1)*fraction;
        int low = (int)Math.floor(position), high = (int)Math.ceil(position);
        return sorted.get(low)+(sorted.get(high)-sorted.get(low))*(position-low);
    }
}
