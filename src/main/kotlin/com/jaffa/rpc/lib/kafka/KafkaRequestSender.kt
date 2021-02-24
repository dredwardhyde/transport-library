package com.jaffa.rpc.lib.kafka

import com.jaffa.rpc.lib.JaffaService
import com.jaffa.rpc.lib.common.OptionConstants
import com.jaffa.rpc.lib.exception.JaffaRpcExecutionException
import com.jaffa.rpc.lib.request.RequestUtils
import com.jaffa.rpc.lib.request.Sender
import com.jaffa.rpc.lib.zookeeper.Utils
import org.apache.kafka.clients.consumer.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.function.Consumer

class KafkaRequestSender : Sender() {
    private val log = LoggerFactory.getLogger(KafkaRequestSender::class.java)

    private val producer: KafkaProducer<String, ByteArray?> = KafkaProducer(JaffaService.producerProps)
    private fun seekTopicsForQuery(cons: KafkaConsumer<String, ByteArray>, query: Map<TopicPartition, Long>) {
        cons.offsetsForTimes(query).forEach{entry ->
            entry.value?.let { cons.seek(entry.key, entry.value.offset()) }
        }
    }

    private fun waitForSyncAnswer(requestTopic: String, requestTime: Long): ByteArray? {
        var consumer: KafkaConsumer<String, ByteArray>?
        do {
            consumer = consumers.poll()
        } while (consumer == null)
        val clientTopicName = requestTopic.replace("-server", "-client")
        val threeMinAgo = Instant.ofEpochMilli(requestTime).minus(3, ChronoUnit.MINUTES).toEpochMilli()
        val finalConsumer: KafkaConsumer<String, ByteArray> = consumer
        val startRebalance = System.nanoTime()
        val query: MutableMap<TopicPartition, Long> = HashMap()
        if (JaffaService.brokersCount == 1) {
            consumer.assign(listOf(TopicPartition(clientTopicName, 0)))
            query[TopicPartition(clientTopicName, 0)] = threeMinAgo
            seekTopicsForQuery(consumer, query)
        } else {
            consumer.subscribe(listOf(clientTopicName), object : ConsumerRebalanceListener {
                override fun onPartitionsRevoked(partitions: Collection<TopicPartition>) {
                    // No-op
                }

                override fun onPartitionsAssigned(partitions: Collection<TopicPartition>) {
                    partitions.forEach(Consumer { x: TopicPartition -> query[x] = threeMinAgo })
                    seekTopicsForQuery(finalConsumer, query)
                    log.debug(">>>>>> Partitions assigned took {} ns", System.nanoTime() - startRebalance)
                }
            })
        }
        consumer.poll(Duration.ofMillis(0))
        log.debug(">>>>>> Consumer rebalance took {} ns", System.nanoTime() - startRebalance)
        val start = System.currentTimeMillis()
        while (!(timeout != -1L && System.currentTimeMillis() - start > timeout || System.currentTimeMillis() - start > 1000 * 60 * 60)) {
            val records = consumer.poll(Duration.ofMillis(10))
            for (record in records) {
                if (record.key() == command?.rqUid) {
                    try {
                        val commits: MutableMap<TopicPartition, OffsetAndMetadata> = HashMap()
                        commits[TopicPartition(record.topic(), record.partition())] = OffsetAndMetadata(record.offset())
                        consumer.commitSync(commits)
                    } catch (e: CommitFailedException) {
                        log.error("Error during commit received answer", e)
                    }
                    consumers.add(consumer)
                    return record.value()
                }
            }
        }
        consumers.add(consumer)
        return null
    }

    public override fun executeSync(message: ByteArray?): ByteArray? {
        val start = System.currentTimeMillis()
        val requestTopic = command?.serviceClass?.let { RequestUtils.getTopicForService(it, moduleId, true) }
        try {
            val resultPackage = ProducerRecord(requestTopic, UUID.randomUUID().toString(), message)
            producer.send(resultPackage).get()
            producer.close()
        } catch (e: Exception) {
            log.error("Error in sending sync request", e)
            throw JaffaRpcExecutionException(e)
        }
        val result = requestTopic?.let { waitForSyncAnswer(it, System.currentTimeMillis()) }
        log.debug(">>>>>> Executed sync request {} in {} ms", command?.rqUid, System.currentTimeMillis() - start)
        return result
    }

    public override fun executeAsync(message: ByteArray?) {
        val start = System.currentTimeMillis()
        val requestTopic = command?.serviceClass?.let { RequestUtils.getTopicForService(it, moduleId, false) }
        try {
            val resultPackage = ProducerRecord(requestTopic, UUID.randomUUID().toString(), message)
            producer.send(resultPackage).get()
            producer.close()
        } catch (e: Exception) {
            log.error("Error while sending async Kafka request", e)
            throw JaffaRpcExecutionException(e)
        }
        log.debug(">>>>>> Executed async request {} in {} ms", command?.rqUid, System.currentTimeMillis() - start)
    }

    companion object {
        private val consumers = ConcurrentLinkedQueue<KafkaConsumer<String, ByteArray>>()

        @kotlin.jvm.JvmStatic
        fun initSyncKafkaConsumers(brokersCount: Int, started: CountDownLatch) {
            val consumerProps = Properties()
            consumerProps["bootstrap.servers"] = Utils.getRequiredOption(OptionConstants.KAFKA_BOOTSTRAP_SERVERS)
            consumerProps["key.deserializer"] = "org.apache.kafka.common.serialization.StringDeserializer"
            consumerProps["value.deserializer"] = "org.apache.kafka.common.serialization.ByteArrayDeserializer"
            consumerProps["enable.auto.commit"] = false.toString()
            consumerProps[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
            if (System.getProperty(OptionConstants.KAFKA_USE_SSL, false.toString()).toBoolean()) {
                val sslProps: MutableMap<String, String?> = HashMap()
                sslProps["security.protocol"] = "SSL"
                sslProps["ssl.truststore.location"] = Utils.getRequiredOption(OptionConstants.KAFKA_SSL_TRUSTSTORE_LOCATION)
                sslProps["ssl.truststore.password"] = Utils.getRequiredOption(OptionConstants.KAFKA_SSL_TRUSTSTORE_PASSWORD)
                sslProps["ssl.keystore.location"] = Utils.getRequiredOption(OptionConstants.KAFKA_SSL_KEYSTORE_LOCATION)
                sslProps["ssl.keystore.password"] = Utils.getRequiredOption(OptionConstants.KAFKA_SSL_KEYSTORE_PASSWORD)
                sslProps["ssl.key.password"] = Utils.getRequiredOption(OptionConstants.KAFKA_SSL_KEY_PASSWORD)
                consumerProps.putAll(sslProps)
            }
            for (i in 0 until brokersCount) {
                consumerProps["group.id"] = UUID.randomUUID().toString()
                val consumer = KafkaConsumer<String, ByteArray>(consumerProps)
                consumers.add(consumer)
                started.countDown()
            }
        }

        fun shutDownConsumers() {
            consumers.forEach(Consumer { obj: KafkaConsumer<String, ByteArray> -> obj.close() })
        }
    }
}