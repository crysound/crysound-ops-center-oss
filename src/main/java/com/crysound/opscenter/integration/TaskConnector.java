package com.crysound.opscenter.integration;

import java.util.List;
import java.util.Map;

/** Connectors own collection and delivery; orchestration owns storage and history. */
public interface TaskConnector {
    String id();
    List<ParameterDefinition> parameters();
    List<DataRecord> collect(Map<String, Object> parameters);
    void push(String runId, List<DataRecord> records);
}
