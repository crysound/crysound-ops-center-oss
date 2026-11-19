package com.crysound.opscenter.integration;

import com.crysound.opscenter.execution.ExecutionException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ConnectorRegistry {
    private final Map<String, TaskConnector> connectors = new LinkedHashMap<>();

    public ConnectorRegistry(List<TaskConnector> supplied) {
        for (TaskConnector connector : supplied) {
            if (connector.id() == null || !connector.id().matches("[a-z][a-z0-9-]{0,63}")
                    || connectors.putIfAbsent(connector.id(), connector) != null) {
                throw new IllegalArgumentException("Invalid or duplicate connector identifier");
            }
        }
    }

    public List<TaskConnector> all() { return List.copyOf(connectors.values()); }

    public TaskConnector require(String id) {
        TaskConnector connector = connectors.get(id);
        if (connector == null) throw new ExecutionException(404, "CONNECTOR_NOT_FOUND", "Unknown connector");
        return connector;
    }
}
