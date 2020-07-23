package com.jaffa.rpc.lib.kafka;

import com.jaffa.rpc.lib.JaffaService;
import com.jaffa.rpc.lib.common.OptionConstants;
import com.jaffa.rpc.lib.exception.JaffaRpcExecutionException;
import com.jaffa.rpc.lib.request.RequestUtils;
import com.jaffa.rpc.lib.request.Sender;
import com.jaffa.rpc.lib.zookeeper.Utils;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.CommitFailedException;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

import static java.time.temporal.ChronoUnit.MINUTES;

@Slf4j
public class KafkaRequestSender extends Sender {

    private static final ConcurrentLinkedQueue<KafkaConsumer<String, byte[]>> consumers = new ConcurrentLinkedQueue<>();
    private final KafkaProducer<String, byte[]> producer = new KafkaProducer<>(JaffaService.getProducerProps());

    public static void initSyncKafkaConsumers(int brokersCount, CountDownLatch started) {
        Properties consumerProps = new Properties();
        consumerProps.put("bootstrap.servers", Utils.getRequiredOption(OptionConstants.KAFKA_BOOTSTRAP_SERVERS));
        consumerProps.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        consumerProps.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        consumerProps.put("enable.auto.commit", String.valueOf(false));
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        if (Boolean.parseBoolean(System.getProperty(OptionConstants.KAFKA_USE_SSL, String.valueOf(false)))) {
            Map<String, String> sslProps = new HashMap<>();
            sslProps.put("security.protocol", "SSL");
            sslProps.put("ssl.truststore.location", Utils.getRequiredOption(OptionConstants.KAFKA_SSL_TRUSTSTORE_LOCATION));
            sslProps.put("ssl.truststore.password", Utils.getRequiredOption(OptionConstants.KAFKA_SSL_TRUSTSTORE_PASSWORD));
            sslProps.put("ssl.keystore.location", Utils.getRequiredOption(OptionConstants.KAFKA_SSL_KEYSTORE_LOCATION));
            sslProps.put("ssl.keystore.password", Utils.getRequiredOption(OptionConstants.KAFKA_SSL_KEYSTORE_PASSWORD));
            sslProps.put("ssl.key.password", Utils.getRequiredOption(OptionConstants.KAFKA_SSL_KEY_PASSWORD));
            consumerProps.putAll(sslProps);
        }

        for (int i = 0; i < brokersCount; i++) {
            consumerProps.put("group.id", UUID.randomUUID().toString());
            KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(consumerProps);
            consumers.add(consumer);
            started.countDown();
        }
    }

    public static void shutDownConsumers() {
        consumers.forEach(KafkaConsumer::close);
    }

    private void seekTopicsForQuery(KafkaConsumer<String, byte[]> cons, Map<TopicPartition, Long> query) {
        for (Map.Entry<TopicPartition, OffsetAndTimestamp> entry : cons.offsetsForTimes(query).entrySet()) {
            if (Objects.isNull(entry.getValue())) continue;
            cons.seek(entry.getKey(), entry.getValue().offset());
        }
    }

    private byte[] waitForSyncAnswer(String requestTopic, long requestTime) {
        KafkaConsumer<String, byte[]> consumer;
        do {
            consumer = consumers.poll();
        } while (consumer == null);
        String clientTopicName = requestTopic.replace("-server", "-client");
        long threeMinAgo = Instant.ofEpochMilli(requestTime).minus(3, MINUTES).toEpochMilli();
        final KafkaConsumer<String, byte[]> finalConsumer = consumer;
        long startRebalance = System.nanoTime();
        Map<TopicPartition, Long> query = new HashMap<>();
        if (JaffaService.getBrokersCount() == 1) {
            consumer.assign(Collections.singletonList(new TopicPartition(clientTopicName, 0)));
            query.put(new TopicPartition(clientTopicName, 0), threeMinAgo);
            seekTopicsForQuery(consumer, query);
        } else {
            consumer.subscribe(Collections.singletonList(clientTopicName), new ConsumerRebalanceListener() {
                @Override
                public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                    // No-op
                }

                @Override
                public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                    partitions.forEach(x -> query.put(x, threeMinAgo));
                    seekTopicsForQuery(finalConsumer, query);
                    log.debug(">>>>>> Partitions assigned took {} ns", System.nanoTime() - startRebalance);
                }
            });
        }
        consumer.poll(Duration.ofMillis(0));
        log.debug(">>>>>> Consumer rebalance took {} ns", System.nanoTime() - startRebalance);
        long start = System.currentTimeMillis();
        while (!((timeout != -1 && System.currentTimeMillis() - start > timeout) || (System.currentTimeMillis() - start > (1000 * 60 * 60)))) {
            ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(10));
            for (ConsumerRecord<String, byte[]> record : records) {
                if (record.key().equals(command.getRqUid())) {
                    try {
                        Map<TopicPartition, OffsetAndMetadata> commits = new HashMap<>();
                        commits.put(new TopicPartition(record.topic(), record.partition()), new OffsetAndMetadata(record.offset()));
                        consumer.commitSync(commits);
                    } catch (CommitFailedException e) {
                        log.error("Error during commit received answer", e);
                    }
                    consumers.add(consumer);
                    return record.value();
                }
            }
        }
        consumers.add(consumer);
        return null;
    }

    @Override
    public byte[] executeSync(byte[] message) {
        long start = System.currentTimeMillis();
        String requestTopic = RequestUtils.getTopicForService(command.getServiceClass(), moduleId, true);
        try {
            ProducerRecord<String, byte[]> resultPackage = new ProducerRecord<>(requestTopic, UUID.randomUUID().toString(), message);
            producer.send(resultPackage).get();
            producer.close();
        } catch (Exception e) {
            log.error("Error in sending sync request", e);
            throw new JaffaRpcExecutionException(e);
        }
        byte[] result = waitForSyncAnswer(requestTopic, System.currentTimeMillis());
        log.debug(">>>>>> Executed sync request {} in {} ms", command.getRqUid(), System.currentTimeMillis() - start);
        return result;
    }

    @Override
    public void executeAsync(byte[] message) {
        try {
            ProducerRecord<String, byte[]> resultPackage = new ProducerRecord<>(RequestUtils.getTopicForService(command.getServiceClass(), moduleId, false), UUID.randomUUID().toString(), message);
            producer.send(resultPackage).get();
            producer.close();
        } catch (Exception e) {
            log.error("Error while sending async Kafka request", e);
            throw new JaffaRpcExecutionException(e);
        }
    }
}
