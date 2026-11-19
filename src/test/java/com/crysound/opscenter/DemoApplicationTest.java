package com.crysound.opscenter;

import com.crysound.opscenter.audit.ExecutionAuditRepository;
import com.crysound.opscenter.demo.DemoConnector;
import com.crysound.opscenter.execution.ExecutionException;
import com.crysound.opscenter.execution.TaskExecutionService;
import com.crysound.opscenter.schedule.DemoSchedule;
import com.crysound.opscenter.storage.RecordRepository;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.net.InetAddress;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DemoApplicationTest {
    private static final String OPERATOR_PASSWORD = UUID.randomUUID().toString();
    private static final String VIEWER_PASSWORD = UUID.randomUUID().toString();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("server.address", () -> InetAddress.getLoopbackAddress().getHostAddress());
        registry.add("server.port", () -> 0);
        registry.add("spring.datasource.url", () -> "jdbc:sqlite::memory:");
        registry.add("ops.security.operator-username", () -> "demo-operator");
        registry.add("ops.security.operator-password", () -> OPERATOR_PASSWORD);
        registry.add("ops.security.viewer-username", () -> "demo-viewer");
        registry.add("ops.security.viewer-password", () -> VIEWER_PASSWORD);
        registry.add("ops.demo.cron", () -> "-");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TaskExecutionService executions;
    @Autowired ExecutionAuditRepository audit;
    @Autowired RecordRepository records;
    @Autowired DemoConnector demo;
    @Autowired DemoSchedule schedule;
    @Autowired HikariDataSource dataSource;

    @Test
    void memoryDatabaseConnectionIsRetainedBetweenUses() throws Exception {
        assertThat(dataSource.getMaximumPoolSize()).isEqualTo(1);
        assertThat(dataSource.getMinimumIdle()).isEqualTo(1);
        assertThat(dataSource.getMaxLifetime()).isZero();
        assertThat(dataSource.getIdleTimeout()).isZero();
        java.sql.Connection physical;
        try (var connection = dataSource.getConnection()) {
            physical = connection.unwrap(org.sqlite.SQLiteConnection.class);
        }
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.unwrap(org.sqlite.SQLiteConnection.class)).isSameAs(physical);
        }
        var run = executions.execute("demo", Map.of(), "MANUAL");
        assertThat(records.find(run.runId())).hasSize(3);
    }

    @BeforeEach
    void clearStoredRuns() {
        jdbc.update("DELETE FROM collected_record");
        jdbc.update("DELETE FROM task_run");
    }

    @Test
    void collectionIsStoredBeforeDeliveryAndQueryable() throws Exception {
        var run = executions.execute("demo", Map.of("size", 2), "MANUAL");
        assertThat(run.status()).isEqualTo("SUCCESS");
        assertThat(run.phase()).isEqualTo("COMPLETE");
        assertThat(run.recordCount()).isEqualTo(2);
        assertThat(records.find(run.runId())).extracting(r -> r.key()).containsExactly("demo-1", "demo-2");
        assertThat(demo.deliveries().get(0).runId()).isEqualTo(run.runId());
        assertThat(demo.deliveries().get(0).records()).isEqualTo(records.find(run.runId()));
        mvc.perform(get("/api/runs/{id}/records", run.runId()).with(user("demo-viewer").roles("VIEWER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].value").value("example-1"));
    }

    @Test
    void invalidParametersCreateNoExecution() {
        assertThatThrownBy(() -> executions.execute("demo", Map.of("size", 11), "MANUAL"))
                .isInstanceOf(ExecutionException.class);
        assertThat(audit.recent()).isEmpty();
    }

    @Test
    void duplicateRecordKeysRollBackTheWholeSnapshot() {
        String runId = UUID.randomUUID().toString();
        audit.start(runId, "demo", "MANUAL");
        var duplicate = new com.crysound.opscenter.integration.DataRecord("demo", "example");
        assertThatThrownBy(() -> records.save(runId, java.util.List.of(duplicate, duplicate)))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(records.find(runId)).isEmpty();
    }

    @Test
    void renderedRecordsEscapeMarkup() throws Exception {
        String runId = UUID.randomUUID().toString();
        audit.start(runId, "demo", "MANUAL");
        records.save(runId, java.util.List.of(new com.crysound.opscenter.integration.DataRecord(
                "demo-markup", "<script>example</script>")));
        mvc.perform(get("/").param("run", runId).with(user("demo-viewer").roles("VIEWER")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;example&lt;/script&gt;")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<script>example</script>"))));
    }

    @Test
    void unknownConnectorCreatesNoExecution() {
        assertThatThrownBy(() -> executions.execute("example-unknown", Map.of(), "MANUAL"))
                .isInstanceOf(ExecutionException.class);
        assertThat(audit.recent()).isEmpty();
    }

    @Test
    void scheduledEntryUsesSamePipeline() {
        schedule.run();
        assertThat(audit.recent()).singleElement().satisfies(run -> {
            assertThat(run.status()).isEqualTo("SUCCESS");
            assertThat(run.triggerType()).isEqualTo("SCHEDULED");
            assertThat(records.find(run.runId())).hasSize(3);
        });
    }

    @Test
    void interruptedExecutionBecomesUnknownWithoutDelivery() {
        String runId = UUID.randomUUID().toString();
        audit.start(runId, "demo", "MANUAL");
        int before = demo.deliveries().size();
        audit.afterSingletonsInstantiated();
        assertThat(audit.require(runId).status()).isEqualTo("UNKNOWN");
        assertThat(audit.require(runId).errorCode()).isEqualTo("INTERRUPTED");
        assertThat(demo.deliveries()).hasSize(before);
    }

    @Test
    void anonymousReadsRequireLogin() throws Exception {
        mvc.perform(get("/api/tasks")).andExpect(status().is3xxRedirection());
    }

    @Test
    void viewerCanReadCatalogButCannotExecute() throws Exception {
        mvc.perform(get("/api/tasks").with(user("demo-viewer").roles("VIEWER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value("demo"));
        mvc.perform(post("/api/tasks/demo/runs").with(user("demo-viewer").roles("VIEWER"))
                        .with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        assertThat(audit.recent()).isEmpty();
    }

    @Test
    void operatorWithoutCsrfCannotExecute() throws Exception {
        mvc.perform(post("/api/tasks/demo/runs").with(user("demo-operator").roles("OPERATOR"))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        assertThat(audit.recent()).isEmpty();
    }

    @Test
    void operatorWithCsrfCanExecuteAndInspectHistory() throws Exception {
        mvc.perform(post("/api/tasks/demo/runs").with(user("demo-operator").roles("OPERATOR"))
                        .with(csrf()).contentType("application/json").content("{\"size\":2}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("SUCCESS"));
        mvc.perform(get("/api/runs").with(user("demo-viewer").roles("VIEWER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].recordCount").value(2));
    }

    @Test
    void invalidAndUnknownApiInputHaveSafeErrors() throws Exception {
        mvc.perform(post("/api/tasks/demo/runs").with(user("demo-operator").roles("OPERATOR"))
                        .with(csrf()).contentType("application/json").content("{\"unexpected\":true}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PARAMETERS"));
        mvc.perform(get("/api/runs/example-missing").with(user("demo-viewer").roles("VIEWER")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RUN_NOT_FOUND"));
    }

    @Test
    void workspaceRendersEmptyAndPopulatedStates() throws Exception {
        mvc.perform(get("/").with(user("demo-operator").roles("OPERATOR")))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("No executions yet")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Run demo")));
        var run = executions.execute("demo", Map.of(), "MANUAL");
        mvc.perform(get("/").param("run", run.runId()).with(user("demo-viewer").roles("VIEWER")))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("example-1")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Run demo"))));
    }

    @Test
    void validGeneratedCredentialAuthenticatesAndIncorrectCredentialDoesNot() throws Exception {
        mvc.perform(formLogin().user("demo-operator").password(OPERATOR_PASSWORD))
                .andExpect(authenticated().withRoles("OPERATOR"));
        mvc.perform(formLogin().user("demo-viewer").password(UUID.randomUUID().toString()))
                .andExpect(unauthenticated());
    }

    @Test
    void htmlFormExecutesAndRedirectsToRun() throws Exception {
        mvc.perform(post("/tasks/demo/run").with(user("demo-operator").roles("OPERATOR"))
                        .with(csrf()).param("size", "2"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("/?run=*"));
        assertThat(audit.recent()).singleElement().satisfies(run -> assertThat(run.status()).isEqualTo("SUCCESS"));
    }
}
