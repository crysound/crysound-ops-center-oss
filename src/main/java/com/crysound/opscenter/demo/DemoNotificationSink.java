package com.crysound.opscenter.demo;

import com.crysound.opscenter.alert.NotificationPort;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.List;

@Component
public class DemoNotificationSink implements NotificationPort {
    private final ArrayDeque<FailureNotice> notices = new ArrayDeque<>();

    @Override
    public synchronized void publish(FailureNotice notice) {
        notices.addFirst(notice);
        while (notices.size() > 20) notices.removeLast();
    }

    public synchronized List<FailureNotice> notices() { return List.copyOf(notices); }
}
