package com.jaffa.rpc.lib.kafka.receivers;

import com.jaffa.rpc.lib.JaffaService;
import com.jaffa.rpc.lib.common.RequestInvocationHelper;
import com.jaffa.rpc.lib.entities.CallbackContainer;
import com.jaffa.rpc.lib.serialization.Serializer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.InterruptException;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

@Slf4j
public class KafkaAsyncResponseReceiver extends KafkaReceiver implements Runnable {

    private final CountDownLatch countDownLatch;

    public KafkaAsyncResponseReceiver(CountDownLatch countDownLatch) {
        this.countDownLatch = countDownLatch;
    }

    @Override
    public void run() {
        JaffaService.getConsumerProps().put("group.id", UUID.randomUUID().toString());
        Runnable consumerThread = () -> {
            KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(JaffaService.getConsumerProps());
            consumer.subscribe(JaffaService.getClientAsyncTopics(), new RebalancedListener(consumer, countDownLatch));
            consumer.poll(Duration.ofMillis(0));
            while (!Thread.currentThread().isInterrupted()) {
                ConsumerRecords<String, byte[]> records = new ConsumerRecords<>(new HashMap<>());
                try {
                    records = consumer.poll(Duration.ofMillis(100));
                } catch (InterruptException ignore) {
                    // No-op
                }
                for (ConsumerRecord<String, byte[]> record : records) {
                    try {
                        CallbackContainer callbackContainer = Serializer.getCurrent().deserialize(record.value(), CallbackContainer.class);
                        RequestInvocationHelper.processCallbackContainer(callbackContainer);
                        Map<TopicPartition, OffsetAndMetadata> commitData = new HashMap<>();
                        commitData.put(new TopicPartition(record.topic(), record.partition()), new OffsetAndMetadata(record.offset()));
                        consumer.commitSync(commitData);
                    } catch (Exception executionException) {
                        log.error("Error during receiving callback", executionException);
                    }
                }
            }
            try {
                consumer.close();
            } catch (InterruptException ignore) {
                // No-op
            }
        };
        startThreadsAndWait(consumerThread);
    }
}
