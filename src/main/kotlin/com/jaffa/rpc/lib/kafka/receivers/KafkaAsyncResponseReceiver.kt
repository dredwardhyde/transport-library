package com.jaffa.rpc.lib.kafka.receivers

import com.jaffa.rpc.lib.JaffaService
import com.jaffa.rpc.lib.common.RequestInvocationHelper
import com.jaffa.rpc.lib.entities.CallbackContainer
import com.jaffa.rpc.lib.serialization.Serializer
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.InterruptException
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*
import java.util.concurrent.CountDownLatch

class KafkaAsyncResponseReceiver(private val countDownLatch: CountDownLatch?) : KafkaReceiver(), Runnable {

    private val log = LoggerFactory.getLogger(KafkaAsyncResponseReceiver::class.java)

    override fun run() {
        JaffaService.consumerProps["group.id"] = UUID.randomUUID().toString()
        val consumerThread = Runnable {
            val consumer = KafkaConsumer<String, ByteArray>(JaffaService.consumerProps)
            consumer.subscribe(JaffaService.clientAsyncTopics, RebalancedListener(consumer, countDownLatch))
            consumer.poll(Duration.ofMillis(0))
            while (!Thread.currentThread().isInterrupted) {
                var records = ConsumerRecords<String, ByteArray>(emptyMap())
                try {
                    records = consumer.poll(Duration.ofMillis(100))
                } catch (ignore: InterruptException) {
                    // No-op
                }
                for (record in records) {
                    try {
                        Serializer.current.deserialize(record.value(), CallbackContainer::class.java)?.let { RequestInvocationHelper.processCallbackContainer(it) }
                        val commitData: MutableMap<TopicPartition, OffsetAndMetadata> = HashMap()
                        commitData[TopicPartition(record.topic(), record.partition())] = OffsetAndMetadata(record.offset())
                        consumer.commitSync(commitData)
                    } catch (executionException: Exception) {
                        log.error("Error during receiving callback", executionException)
                    }
                }
            }
            try {
                consumer.close()
            } catch (ignore: InterruptException) {
                // No-op
            }
        }
        startThreadsAndWait(consumerThread)
    }
}