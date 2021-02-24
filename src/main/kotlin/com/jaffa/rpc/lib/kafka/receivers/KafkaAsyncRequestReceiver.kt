package com.jaffa.rpc.lib.kafka.receivers

import com.jaffa.rpc.lib.JaffaService
import com.jaffa.rpc.lib.common.RequestInvocationHelper
import com.jaffa.rpc.lib.entities.Command
import com.jaffa.rpc.lib.serialization.Serializer
import com.jaffa.rpc.lib.zookeeper.Utils
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.InterruptException
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import kotlin.collections.HashMap

class KafkaAsyncRequestReceiver(private val countDownLatch: CountDownLatch?) : KafkaReceiver(), Runnable {

    private val log = LoggerFactory.getLogger(KafkaAsyncRequestReceiver::class.java)

    override fun run() {
        JaffaService.consumerProps["group.id"] = UUID.randomUUID().toString()
        val consumerThread = Runnable {
            val consumer = KafkaConsumer<String, ByteArray>(JaffaService.consumerProps)
            val producer = KafkaProducer<String, ByteArray>(JaffaService.producerProps)
            consumer.subscribe(JaffaService.serverAsyncTopics, RebalancedListener(consumer, countDownLatch))
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
                        val command = Serializer.current.deserialize(record.value(), Command::class.java)
                        producer.send(
                                ProducerRecord(
                                        Utils.getServiceInterfaceNameFromClient(command?.serviceClass) + "-" + command?.sourceModuleId + "-client-async",
                                        UUID.randomUUID().toString(),
                                        Serializer.current.serialize(command?.let {
                                            RequestInvocationHelper.constructCallbackContainer(
                                                    it, RequestInvocationHelper.invoke(command)
                                            )
                                        })
                                )
                        ).get()
                        val commitData: MutableMap<TopicPartition, OffsetAndMetadata> = HashMap()
                        commitData[TopicPartition(record.topic(), record.partition())] =
                                OffsetAndMetadata(record.offset())
                        consumer.commitSync(commitData)
                    } catch (systemException: InterruptedException) {
                        log.error("General Kafka exception", systemException)
                    } catch (systemException: ExecutionException) {
                        log.error("General Kafka exception", systemException)
                    } catch (executionException: Exception) {
                        log.error("Async request execution exception", executionException)
                    }
                }
            }
            try {
                consumer.close()
                producer.close()
            } catch (ignore: InterruptException) {
                // No-op
            }
        }
        startThreadsAndWait(consumerThread)
    }
}