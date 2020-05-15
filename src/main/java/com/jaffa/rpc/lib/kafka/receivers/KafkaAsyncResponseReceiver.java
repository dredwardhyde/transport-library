package com.jaffa.rpc.lib.kafka.receivers;

import com.jaffa.rpc.lib.JaffaService;
import com.jaffa.rpc.lib.common.RequestInvoker;
import com.jaffa.rpc.lib.entities.CallbackContainer;
import com.jaffa.rpc.lib.exception.JaffaRpcExecutionException;
import com.jaffa.rpc.lib.serialization.Serializer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.InterruptException;

import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import static java.time.temporal.ChronoUnit.MINUTES;

@Slf4j
public class KafkaAsyncResponseReceiver extends KafkaReceiver implements Runnable {

    private final CountDownLatch countDownLatch;

    public KafkaAsyncResponseReceiver(CountDownLatch countDownLatch) {
        this.countDownLatch = countDownLatch;
    }

    @Override
    public void run() {
        JaffaService.getConsumerProps().put("group.id", UUID.randomUUID().toString());
        long startRebalance = System.nanoTime();
        long threeMinAgo = Instant.ofEpochMilli(System.currentTimeMillis()).minus(3, MINUTES).toEpochMilli();
        Runnable consumerThread = () -> {
            KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(JaffaService.getConsumerProps());
            consumer.subscribe(JaffaService.getClientAsyncTopics(), new ConsumerRebalanceListener() {
                @Override
                public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                    // No-op
                }

                @Override
                public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                    Map<TopicPartition, Long> query = new HashMap<>();
                    partitions.forEach(x -> query.put(x, threeMinAgo));
                    for (Map.Entry<TopicPartition, OffsetAndTimestamp> entry : consumer.offsetsForTimes(query).entrySet()) {
                        if (entry.getValue() == null) continue;
                        consumer.seek(entry.getKey(), entry.getValue().offset());
                    }
                    log.info(">>>>>> Partitions assigned took {} ns", System.nanoTime() - startRebalance);
                    countDownLatch.countDown();
                }
            });
            consumer.poll(Duration.ofMillis(0));
            while (!Thread.currentThread().isInterrupted()) {
                ConsumerRecords<String, byte[]> records = new ConsumerRecords<>(new HashMap<>());
                try {
                    records = consumer.poll(Duration.ofMillis(100));
                } catch (InterruptException ignore) {
                }
                for (ConsumerRecord<String, byte[]> record : records) {
                    try {
                        CallbackContainer callbackContainer = Serializer.getCtx().deserialize(record.value(), CallbackContainer.class);
                        RequestInvoker.processCallbackContainer(callbackContainer);
                        Map<TopicPartition, OffsetAndMetadata> commitData = new HashMap<>();
                        commitData.put(new TopicPartition(record.topic(), record.partition()), new OffsetAndMetadata(record.offset()));
                        consumer.commitSync(commitData);
                    } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | InvocationTargetException | IllegalAccessException executionException) {
                        log.error("Error during receiving callback", executionException);
                        throw new JaffaRpcExecutionException(executionException);
                    }
                }
            }
            try {
                consumer.close();
            } catch (InterruptException ignore) {
            }
        };
        startThreadsAndWait(consumerThread);
    }
}
