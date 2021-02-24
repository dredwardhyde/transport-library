package com.jaffa.rpc.lib.common

import com.jaffa.rpc.lib.entities.Command
import com.jaffa.rpc.lib.exception.JaffaRpcExecutionTimeoutException
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

object FinalizationHelper {

    private val log = LoggerFactory.getLogger(FinalizationHelper::class.java)

    val eventsToConsume: ConcurrentMap<String, Command> = ConcurrentHashMap()
    var executor: ScheduledExecutorService? = null
    var context: ApplicationContext? = null
    private val finalizerThread = Runnable {
        eventsToConsume.values
                .stream()
                .filter { x: Command -> x.asyncExpireTime < System.currentTimeMillis() }
                .forEach { command: Command ->
                    try {
                        if (eventsToConsume.remove(command.callbackKey) != null) {
                            val start = System.nanoTime()
                            log.debug("Finalization request {}", command.rqUid)
                            val callbackClass = Class.forName(command.callbackClass)
                            val method = callbackClass.getMethod("onError", String::class.java, Throwable::class.java)
                            method.invoke(context?.getBean(callbackClass), command.callbackKey, JaffaRpcExecutionTimeoutException())
                            log.debug("Finalization request {} took {}ns", command.rqUid, System.nanoTime() - start)
                        }
                    } catch (e: Exception) {
                        log.error("Error during finalization command: {}", command)
                    }
                }
    }

    fun startFinalizer(context: ApplicationContext?) {
        FinalizationHelper.context = context
        executor = Executors.newSingleThreadScheduledExecutor()
        executor?.scheduleAtFixedRate(finalizerThread, 0, 5, TimeUnit.MILLISECONDS)
        log.info("Finalizer thread started")
    }

    fun stopFinalizer() {
        executor?.shutdown()
        log.info("Finalizer thread stopped")
    }
}