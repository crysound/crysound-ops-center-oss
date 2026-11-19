package com.crysound.opscenter.schedule;

import com.crysound.opscenter.execution.TaskExecutionService;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Map;

@Configuration
@EnableScheduling
public class DemoSchedule {
    private final TaskExecutionService executions;

    public DemoSchedule(TaskExecutionService executions) { this.executions = executions; }

    @Scheduled(cron = "${ops.demo.cron}", zone = "UTC")
    public void run() { executions.execute("demo", Map.of(), "SCHEDULED"); }
}
