package com.crysound.opscenter.alert;

/** Provider-independent notification boundary. */
public interface NotificationPort {
    void publish(FailureNotice notice);

    record FailureNotice(String runId, String connectorId, String phase, String code) { }
}
