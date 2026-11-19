package com.crysound.opscenter.storage;

import com.crysound.opscenter.integration.DataRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public class RecordRepository {
    private final JdbcTemplate jdbc;

    public RecordRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public void save(String runId, List<DataRecord> records) {
        for (DataRecord record : records) {
            jdbc.update("INSERT INTO collected_record(run_id,record_key,record_value) VALUES (?,?,?)",
                    runId, record.key(), record.value());
        }
    }

    public List<DataRecord> find(String runId) {
        return jdbc.query("SELECT record_key,record_value FROM collected_record WHERE run_id=? ORDER BY record_key",
                (r, n) -> new DataRecord(r.getString(1), r.getString(2)), runId);
    }
}
