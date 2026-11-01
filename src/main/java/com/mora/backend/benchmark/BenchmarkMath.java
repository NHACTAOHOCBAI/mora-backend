package com.mora.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

public final class BenchmarkMath {
    private BenchmarkMath() {}
    public static double citationAccuracy(JsonNode citations, JsonNode sources, JsonNode documents) {
        Map<Long,String> codes = new HashMap<>();
        for (var d : documents) codes.put(d.path("documentId").asLong(), d.path("code").asText());
        Set<String> expected = new HashSet<>(), actual = new HashSet<>();
        for (var s : sources) expected.add(s.path("documentCode").asText()+":"+s.path("pageNumber").asInt());
        for (var c : citations) {
            long id = c.path("documentId").asLong(-1);
            // Unknown or missing document IDs remain incorrect, even if the page number matches.
            actual.add(codes.getOrDefault(id,"unknown-"+id)+":"+c.path("pageNumber").asInt());
        }
        return actual.isEmpty() ? 0 : actual.stream().filter(expected::contains).count() / (double)actual.size();
    }
    public static double percentile(List<Double> values, double fraction) {
        if (values.isEmpty()) return 0;
        var sorted = values.stream().sorted().toList();
        double position = (sorted.size()-1)*fraction;
        int low = (int)Math.floor(position), high = (int)Math.ceil(position);
        return sorted.get(low)+(sorted.get(high)-sorted.get(low))*(position-low);
    }
}
