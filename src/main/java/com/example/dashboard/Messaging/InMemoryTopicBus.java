package com.example.dashboard.Messaging;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InMemoryTopicBus {
    private final Map<String, CopyOnWriteArrayList<TopicSubscription>> subscriptionsByTopic = new ConcurrentHashMap<>();

    public TopicSubscription subscribe(String topic) {
        Objects.requireNonNull(topic, "topic");
        CopyOnWriteArrayList<TopicSubscription> list =
                subscriptionsByTopic.computeIfAbsent(topic, ignored -> new CopyOnWriteArrayList<>());

        final TopicSubscription[] ref = new TopicSubscription[1];
        TopicSubscription subscription = new TopicSubscription(topic, () -> list.remove(ref[0]));
        ref[0] = subscription;
        list.add(subscription);
        return subscription;
    }

    public void publishSse(String topic, String eventName, String dataJson) {
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(eventName, "eventName");
        Objects.requireNonNull(dataJson, "dataJson");

        String message = "event: " + eventName + "\n" + "data: " + dataJson + "\n\n";
        List<TopicSubscription> subs = subscriptionsByTopic.get(topic);
        if (subs == null || subs.isEmpty()) {
            return;
        }
        for (TopicSubscription sub : subs) {
            sub.enqueue(message);
        }
    }
}
