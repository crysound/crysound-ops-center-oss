package com.crysound.opscenter.web;

import com.crysound.opscenter.audit.ExecutionAuditRepository;
import com.crysound.opscenter.demo.DemoConnector;
import com.crysound.opscenter.execution.ExecutionRecord;
import com.crysound.opscenter.execution.TaskExecutionService;
import com.crysound.opscenter.integration.ConnectorRegistry;
import com.crysound.opscenter.storage.RecordRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@Controller
public class DashboardController {
    private final ConnectorRegistry registry;
    private final TaskExecutionService executions;
    private final ExecutionAuditRepository audit;
    private final RecordRepository records;
    private final DemoConnector demo;

    public DashboardController(ConnectorRegistry registry, TaskExecutionService executions,
                                ExecutionAuditRepository audit, RecordRepository records, DemoConnector demo) {
        this.registry = registry;
        this.executions = executions;
        this.audit = audit;
        this.records = records;
        this.demo = demo;
    }

    @GetMapping("/")
    public String dashboard(@RequestParam(required = false) String run, Authentication authentication, Model model) {
        List<ExecutionRecord> recent = audit.recent();
        ExecutionRecord selected = run != null ? audit.require(run) : recent.stream().findFirst().orElse(null);
        model.addAttribute("tasks", registry.all());
        model.addAttribute("runs", recent);
        model.addAttribute("selected", selected);
        model.addAttribute("records", selected == null ? List.of() : records.find(selected.runId()));
        model.addAttribute("deliveries", demo.deliveries());
        model.addAttribute("canExecute", authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_OPERATOR".equals(a.getAuthority())));
        return "dashboard";
    }

    @PostMapping("/tasks/{id}/run")
    public String execute(@PathVariable String id, @RequestParam int size) {
        ExecutionRecord run = executions.execute(id, Map.of("size", size), "MANUAL");
        return "redirect:/?run=" + run.runId();
    }

    @GetMapping("/login")
    public String login() { return "login"; }
}
