package com.jaffa.rpc.lib.kafka.receivers

import com.jaffa.rpc.lib.JaffaService
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.text.MessageFormat
import java.util.*
import java.util.function.Consumer

abstract class KafkaReceiver : Closeable, Runnable {

    private val log = LoggerFactory.getLogger(KafkaReceiver::class.java)
    private val threads = ArrayList<Thread>(JaffaService.brokersCount)

    protected fun startThreadsAndWait(runnable: Runnable?) {
        for (i in 0 until JaffaService.brokersCount) {
            threads.add(Thread(runnable))
        }
        threads.forEach(Consumer { obj: Thread -> obj.start() })
        threads.forEach(Consumer { x: Thread ->
            try {
                x.join()
            } catch (e: InterruptedException) {
                log.error(MessageFormat.format("Can not join thread {0} in {1}", x.name, this.javaClass.simpleName), e)
            }
        })
    }

    override fun close() {
        for (thread in threads) {
            do {
                thread.interrupt()
            } while (thread.state != Thread.State.TERMINATED)
            log.info("Thread {} from {} terminated", thread.name, this.javaClass.simpleName)
        }
        log.info("{} terminated", this.javaClass.simpleName)
    }
}