package com.example.dashboard.Messaging;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TopicSubscription implements AutoCloseable {
    private final String topic;
    private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Runnable onClose;

    TopicSubscription(String topic, Runnable onClose) {
        this.topic = Objects.requireNonNull(topic, "topic");
        this.onClose = Objects.requireNonNull(onClose, "onClose");
    }

    public String topic() {
        return topic;
    }

    public void enqueue(String message) {
        if (closed.get()) {
            return;
        }
        messages.offer(message);
    }

    public String take() throws InterruptedException {
        return messages.take();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        onClose.run();
    }
}

