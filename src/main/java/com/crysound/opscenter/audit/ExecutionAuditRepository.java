package com.crysound.opscenter.audit;

import com.crysound.opscenter.execution.ExecutionException;
import com.crysound.opscenter.execution.ExecutionRecord;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public class ExecutionAuditRepository implements SmartInitializingSingleton {
    private static final RowMapper<ExecutionRecord> ROW = (r, n) -> new ExecutionRecord(
            r.getString("run_id"), r.getString("connector_id"), r.getString("trigger_type"),
            r.getString("status"), r.getString("phase"), r.getInt("record_count"),
            r.getString("error_code"), r.getString("started_at"), r.getString("finished_at"));
    private final JdbcTemplate jdbc;

    public ExecutionAuditRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void afterSingletonsInstantiated() {
        // Interrupted delivery is uncertain; do not replay it automatically.
        jdbc.update("UPDATE task_run SET status='UNKNOWN',error_code='INTERRUPTED',finished_at=? WHERE status='RUNNING'",
                Instant.now().toString());
    }

    public void start(String runId, String connectorId, String trigger) {
        jdbc.update("INSERT INTO task_run(run_id,connector_id,trigger_type,status,phase,started_at) VALUES (?,?,?,'RUNNING','COLLECT',?)",
                runId, connectorId, trigger, Instant.now().toString());
    }

    public void phase(String runId, String phase) {
        jdbc.update("UPDATE task_run SET phase=? WHERE run_id=?", phase, runId);
    }

    public void finish(String runId, String status, String phase, int count, String errorCode) {
        jdbc.update("UPDATE task_run SET status=?,phase=?,record_count=?,error_code=?,finished_at=? WHERE run_id=?",
                status, phase, count, errorCode, Instant.now().toString(), runId);
    }

    public ExecutionRecord require(String runId) {
        return jdbc.query("SELECT * FROM task_run WHERE run_id=?", ROW, runId).stream().findFirst()
                .orElseThrow(() -> new ExecutionException(404, "RUN_NOT_FOUND", "Unknown execution"));
    }

    public List<ExecutionRecord> recent() {
        return jdbc.query("SELECT * FROM task_run ORDER BY started_at DESC,run_id LIMIT 50", ROW);
    }
}
