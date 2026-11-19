package com.crysound.opscenter.execution;

import com.crysound.opscenter.alert.NotificationPort;
import com.crysound.opscenter.audit.ExecutionAuditRepository;
import com.crysound.opscenter.integration.ConnectorRegistry;
import com.crysound.opscenter.integration.DataRecord;
import com.crysound.opscenter.integration.TaskConnector;
import com.crysound.opscenter.storage.RecordRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TaskExecutionService {
    private final ConnectorRegistry registry;
    private final TaskParameterValidator validator;
    private final ExecutionAuditRepository audit;
    private final RecordRepository records;
    private final NotificationPort notifications;

    public TaskExecutionService(ConnectorRegistry registry, TaskParameterValidator validator,
                                ExecutionAuditRepository audit, RecordRepository records,
                                NotificationPort notifications) {
        this.registry = registry;
        this.validator = validator;
        this.audit = audit;
        this.records = records;
        this.notifications = notifications;
    }

    /** Serializes manual and scheduled work within one process. */
    public synchronized ExecutionRecord execute(String connectorId, Map<String, Object> supplied, String trigger) {
        TaskConnector connector = registry.require(connectorId);
        Map<String, Object> parameters = validator.validate(connector.parameters(), supplied);
        String runId = UUID.randomUUID().toString();
        audit.start(runId, connectorId, trigger);
        String phase = "COLLECT";
        int count = 0;
        try {
            List<DataRecord> collected = List.copyOf(connector.collect(parameters));
            if (collected.size() > 1000) throw new IllegalArgumentException("Batch limit exceeded");
            phase = "STORE";
            audit.phase(runId, phase);
            records.save(runId, collected);
            count = collected.size();
            phase = "PUSH";
            audit.phase(runId, phase);
            // Deliver the committed snapshot rather than the original transient batch.
            connector.push(runId, records.find(runId));
            audit.finish(runId, "SUCCESS", "COMPLETE", count, null);
        } catch (RuntimeException failure) {
            String code = phase + "_FAILED";
            audit.finish(runId, "FAILED", phase, count, code);
            try {
                notifications.publish(new NotificationPort.FailureNotice(runId, connectorId, phase, code));
            } catch (RuntimeException deliveryFailure) {
                // Notification failure does not change the execution outcome.
            }
        }
        return audit.require(runId);
    }
}
