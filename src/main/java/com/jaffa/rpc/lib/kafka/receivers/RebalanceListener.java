package com.jaffa.rpc.lib.kafka.receivers;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.TopicPartition;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static java.time.temporal.ChronoUnit.MINUTES;

@Slf4j
@AllArgsConstructor
public class RebalanceListener implements ConsumerRebalanceListener {
    private final Consumer<String, byte[]> consumer;
    private final CountDownLatch countDownLatch;

    @Override
    public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
        // No-op
    }

    @Override
    public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
        long startRebalance = System.nanoTime();
        long threeMinAgo = Instant.ofEpochMilli(System.currentTimeMillis()).minus(3, MINUTES).toEpochMilli();
        Map<TopicPartition, Long> query = new HashMap<>();
        partitions.forEach(x -> query.put(x, threeMinAgo));
        for (Map.Entry<TopicPartition, OffsetAndTimestamp> entry : consumer.offsetsForTimes(query).entrySet()) {
            if (entry.getValue() == null) continue;
            consumer.seek(entry.getKey(), entry.getValue().offset());
        }
        countDownLatch.countDown();
        log.info(">>>>>> Partitions assigned took {} ns, latch {}", System.nanoTime() - startRebalance, countDownLatch.getCount());
    }
}
