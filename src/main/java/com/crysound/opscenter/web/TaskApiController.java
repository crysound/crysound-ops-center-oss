package com.crysound.opscenter.web;

import com.crysound.opscenter.audit.ExecutionAuditRepository;
import com.crysound.opscenter.demo.DemoConnector;
import com.crysound.opscenter.demo.DemoNotificationSink;
import com.crysound.opscenter.execution.ExecutionRecord;
import com.crysound.opscenter.execution.TaskExecutionService;
import com.crysound.opscenter.integration.ConnectorRegistry;
import com.crysound.opscenter.integration.DataRecord;
import com.crysound.opscenter.integration.ParameterDefinition;
import com.crysound.opscenter.storage.RecordRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TaskApiController {
    private final ConnectorRegistry registry;
    private final TaskExecutionService executions;
    private final ExecutionAuditRepository audit;
    private final RecordRepository records;
    private final DemoConnector demo;
    private final DemoNotificationSink notifications;

    public TaskApiController(ConnectorRegistry registry, TaskExecutionService executions,
                             ExecutionAuditRepository audit, RecordRepository records,
                             DemoConnector demo, DemoNotificationSink notifications) {
        this.registry = registry;
        this.executions = executions;
        this.audit = audit;
        this.records = records;
        this.demo = demo;
        this.notifications = notifications;
    }

    public record TaskView(String id, List<ParameterDefinition> parameters) { }

    @GetMapping("/tasks")
    public List<TaskView> tasks() {
        return registry.all().stream().map(c -> new TaskView(c.id(), c.parameters())).toList();
    }

    @PostMapping("/tasks/{id}/runs")
    @ResponseStatus(HttpStatus.CREATED)
    public ExecutionRecord execute(@PathVariable String id, @RequestBody Map<String, Object> parameters) {
        return executions.execute(id, parameters, "MANUAL");
    }

    @GetMapping("/runs")
    public List<ExecutionRecord> runs() { return audit.recent(); }

    @GetMapping("/runs/{id}")
    public ExecutionRecord run(@PathVariable String id) { return audit.require(id); }

    @GetMapping("/runs/{id}/records")
    public List<DataRecord> records(@PathVariable String id) {
        audit.require(id);
        return records.find(id);
    }

    @GetMapping("/demo/deliveries")
    public List<DemoConnector.Delivery> deliveries() { return demo.deliveries(); }

    @GetMapping("/demo/notices")
    public Object notices() { return notifications.notices(); }
}
