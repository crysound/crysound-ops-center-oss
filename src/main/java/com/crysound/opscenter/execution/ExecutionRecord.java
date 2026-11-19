package com.crysound.opscenter.execution;

public record ExecutionRecord(String runId, String connectorId, String triggerType,
                              String status, String phase, int recordCount, String errorCode,
                              String startedAt, String finishedAt) { }
