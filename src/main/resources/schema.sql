CREATE TABLE IF NOT EXISTS task_run (
    run_id TEXT PRIMARY KEY,
    connector_id TEXT NOT NULL,
    trigger_type TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED', 'UNKNOWN')),
    phase TEXT NOT NULL,
    record_count INTEGER NOT NULL DEFAULT 0,
    error_code TEXT,
    started_at TEXT NOT NULL,
    finished_at TEXT
);
CREATE TABLE IF NOT EXISTS collected_record (
    run_id TEXT NOT NULL REFERENCES task_run(run_id),
    record_key TEXT NOT NULL,
    record_value TEXT NOT NULL,
    PRIMARY KEY (run_id, record_key)
);
CREATE INDEX IF NOT EXISTS task_run_started ON task_run(started_at DESC, run_id);
