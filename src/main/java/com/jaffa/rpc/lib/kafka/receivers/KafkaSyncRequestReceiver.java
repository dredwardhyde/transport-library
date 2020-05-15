package com.jaffa.rpc.lib.kafka.receivers;

import com.jaffa.rpc.lib.JaffaService;
import com.jaffa.rpc.lib.common.RequestInvoker;
import com.jaffa.rpc.lib.entities.Command;
import com.jaffa.rpc.lib.entities.RequestContext;
import com.jaffa.rpc.lib.exception.JaffaRpcSystemException;
import com.jaffa.rpc.lib.serialization.Serializer;
import com.jaffa.rpc.lib.zookeeper.Utils;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.InterruptException;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

import static java.time.temporal.ChronoUnit.MINUTES;

@Slf4j
public class KafkaSyncRequestReceiver extends KafkaReceiver implements Runnable {

    private final CountDownLatch countDownLatch;

    public KafkaSyncRequestReceiver(CountDownLatch countDownLatch) {
        this.countDownLatch = countDownLatch;
    }

    @Override
    public void run() {
        JaffaService.getConsumerProps().put("group.id", UUID.randomUUID().toString());
        Runnable consumerThread = () -> {
            KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(JaffaService.getConsumerProps());
            KafkaProducer<String, byte[]> producer = new KafkaProducer<>(JaffaService.getProducerProps());
            long startRebalance = System.nanoTime();
            long threeMinAgo = Instant.ofEpochMilli(System.currentTimeMillis()).minus(3, MINUTES).toEpochMilli();
            consumer.subscribe(JaffaService.getServerSyncTopics(), new ConsumerRebalanceListener() {
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
                        Command command = Serializer.getCtx().deserialize(record.value(), Command.class);
                        RequestContext.setMetaData(command);
                        Object result = RequestInvoker.invoke(command);
                        byte[] serializedResponse = Serializer.getCtx().serializeWithClass(RequestInvoker.getResult(result));
                        ProducerRecord<String, byte[]> resultPackage = new ProducerRecord<>(Utils.getServiceInterfaceNameFromClient(command.getServiceClass()) + "-" + Utils.getRequiredOption("jaffa.rpc.module.id") + "-client-sync", command.getRqUid(), serializedResponse);
                        producer.send(resultPackage).get();
                        Map<TopicPartition, OffsetAndMetadata> commitData = new HashMap<>();
                        commitData.put(new TopicPartition(record.topic(), record.partition()), new OffsetAndMetadata(record.offset()));
                        consumer.commitSync(commitData);
                    } catch (ExecutionException | InterruptedException executionException) {
                        log.error("Target method execution exception", executionException);
                        throw new JaffaRpcSystemException(executionException);
                    }
                }
            }
            try {
                consumer.close();
                producer.close();
            } catch (InterruptException ignore) {
            }
        };
        startThreadsAndWait(consumerThread);
    }
}
