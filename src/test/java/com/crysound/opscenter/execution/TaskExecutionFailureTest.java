package com.crysound.opscenter.execution;

import com.crysound.opscenter.alert.NotificationPort;
import com.crysound.opscenter.audit.ExecutionAuditRepository;
import com.crysound.opscenter.integration.ConnectorRegistry;
import com.crysound.opscenter.integration.DataRecord;
import com.crysound.opscenter.integration.TaskConnector;
import com.crysound.opscenter.storage.RecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TaskExecutionFailureTest {
    private final TaskConnector connector = mock(TaskConnector.class);
    private final ExecutionAuditRepository audit = mock(ExecutionAuditRepository.class);
    private final RecordRepository records = mock(RecordRepository.class);
    private final NotificationPort notifications = mock(NotificationPort.class);
    private TaskExecutionService executions;

    @BeforeEach
    void setUp() {
        when(connector.id()).thenReturn("demo");
        when(connector.parameters()).thenReturn(List.of());
        when(connector.collect(anyMap())).thenReturn(List.of(new DataRecord("demo", "example")));
        when(records.find(anyString())).thenReturn(List.of(new DataRecord("demo", "example")));
        executions = new TaskExecutionService(new ConnectorRegistry(List.of(connector)),
                new TaskParameterValidator(), audit, records, notifications);
    }

    @Test
    void collectionFailureDoesNotStoreOrPushAndDoesNotExposeExceptionText() {
        when(connector.collect(anyMap())).thenThrow(new IllegalStateException("example-private-detail"));
        executions.execute("demo", Map.of(), "MANUAL");
        verifyNoInteractions(records);
        verify(connector, never()).push(anyString(), anyList());
        verify(audit).finish(anyString(), eq("FAILED"), eq("COLLECT"), eq(0), eq("COLLECT_FAILED"));
        ArgumentCaptor<NotificationPort.FailureNotice> notice = ArgumentCaptor.forClass(NotificationPort.FailureNotice.class);
        verify(notifications).publish(notice.capture());
        assertThat(notice.getValue().toString()).doesNotContain("example-private-detail");
    }

    @Test
    void storageFailureDoesNotPush() {
        doThrow(new IllegalStateException()).when(records).save(anyString(), anyList());
        executions.execute("demo", Map.of(), "MANUAL");
        verify(connector, never()).push(anyString(), anyList());
        verify(audit).finish(anyString(), eq("FAILED"), eq("STORE"), eq(0), eq("STORE_FAILED"));
    }

    @Test
    void pushFailurePreservesStoredCountAndNotifies() {
        doThrow(new IllegalStateException()).when(connector).push(anyString(), anyList());
        executions.execute("demo", Map.of(), "MANUAL");
        verify(records).save(anyString(), anyList());
        verify(audit).finish(anyString(), eq("FAILED"), eq("PUSH"), eq(1), eq("PUSH_FAILED"));
        verify(notifications).publish(any());
    }

    @Test
    void notificationFailureDoesNotOverwriteFailedExecution() {
        when(connector.collect(anyMap())).thenThrow(new IllegalStateException());
        doThrow(new IllegalStateException()).when(notifications).publish(any());
        executions.execute("demo", Map.of(), "MANUAL");
        verify(audit).finish(anyString(), eq("FAILED"), eq("COLLECT"), eq(0), eq("COLLECT_FAILED"));
        verify(audit, never()).finish(anyString(), eq("SUCCESS"), anyString(), anyInt(), any());
    }

    @Test
    void pushReceivesStoredSnapshotAndRunsAfterStorage() {
        var stored = List.of(new DataRecord("demo-stored", "example-stored"));
        when(records.find(anyString())).thenReturn(stored);
        executions.execute("demo", Map.of(), "MANUAL");
        var order = inOrder(connector, records, audit);
        order.verify(connector).collect(anyMap());
        order.verify(records).save(anyString(), anyList());
        order.verify(records).find(anyString());
        order.verify(connector).push(anyString(), eq(stored));
        order.verify(audit).finish(anyString(), eq("SUCCESS"), eq("COMPLETE"), eq(1), isNull());
        verifyNoInteractions(notifications);
    }
}
