package com.jaffa.rpc.lib.kafka.receivers

import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.function.Consumer

class RebalancedListener(
        val consumer: org.apache.kafka.clients.consumer.Consumer<String, ByteArray>?,
        private val countDownLatch: CountDownLatch?
) : ConsumerRebalanceListener {

    private val log = LoggerFactory.getLogger(KafkaAsyncResponseReceiver::class.java)

    override fun onPartitionsRevoked(partitions: Collection<TopicPartition>) {
        // No-op
    }

    override fun onPartitionsAssigned(partitions: Collection<TopicPartition>) {
        val startRebalanced = System.nanoTime()
        val threeMinAgo = Instant.ofEpochMilli(System.currentTimeMillis()).minus(3, ChronoUnit.MINUTES).toEpochMilli()
        val query: MutableMap<TopicPartition, Long> = HashMap()
        partitions.forEach(Consumer { x: TopicPartition -> query[x] = threeMinAgo })
        consumer?.offsetsForTimes(query)?.forEach { entry -> entry.value?.let { consumer.seek(entry.key, entry.value.offset()) } }
        countDownLatch?.countDown()
        log.debug(">>>>>> Partitions assigned took {} ns, latch {}", System.nanoTime() - startRebalanced, countDownLatch?.count)
    }
}