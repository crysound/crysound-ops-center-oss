package com.crysound.opscenter.demo;

import com.crysound.opscenter.integration.DataRecord;
import com.crysound.opscenter.integration.ParameterDefinition;
import com.crysound.opscenter.integration.TaskConnector;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/** Deterministic in-memory source and bounded in-memory destination. */
@Component
public class DemoConnector implements TaskConnector {
    private final ArrayDeque<Delivery> deliveries = new ArrayDeque<>();

    public record Delivery(String runId, List<DataRecord> records) { }

    @Override
    public String id() { return "demo"; }

    @Override
    public List<ParameterDefinition> parameters() {
        return List.of(new ParameterDefinition("size", "Record count", "integer", false, List.of(), null, 1L, 10L));
    }

    @Override
    public List<DataRecord> collect(Map<String, Object> parameters) {
        int size = ((Number) parameters.getOrDefault("size", 3L)).intValue();
        if (size < 1 || size > 10) throw new IllegalArgumentException("Invalid record count");
        return IntStream.rangeClosed(1, size)
                .mapToObj(i -> new DataRecord("demo-" + i, "example-" + i)).toList();
    }

    @Override
    public synchronized void push(String runId, List<DataRecord> records) {
        deliveries.addFirst(new Delivery(runId, List.copyOf(records)));
        while (deliveries.size() > 20) deliveries.removeLast();
    }

    public synchronized List<Delivery> deliveries() { return List.copyOf(deliveries); }
}
