package com.jaffa.rpc.lib.request

import com.jaffa.rpc.lib.callbacks.Callback
import com.jaffa.rpc.lib.common.FinalizationHelper
import com.jaffa.rpc.lib.common.OptionConstants
import com.jaffa.rpc.lib.entities.Command
import com.jaffa.rpc.lib.entities.ExceptionHolder
import com.jaffa.rpc.lib.exception.JaffaRpcExecutionException
import com.jaffa.rpc.lib.exception.JaffaRpcSystemException
import com.jaffa.rpc.lib.ui.AdminServer
import com.jaffa.rpc.lib.zookeeper.Utils
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

class RequestImpl<T>(private val command: Command?) : Request<T> {

    private val log = LoggerFactory.getLogger(FinalizationHelper::class.java)

    private var sender: Sender? = null

    private var timeout: Long = -1

    private var moduleId: String? = null

    override fun withTimeout(timeout: Long?, unit: TimeUnit?): RequestImpl<T> {
        require(unit != null) { OptionConstants.ILLEGAL_ARGS_MESSAGE }
        if (timeout != null) {
            require(timeout >= 0) { OptionConstants.NEGATIVE_TIMEOUT_MESSAGE }
            this.timeout = timeout.let { unit.toMillis(it) }
        }
        return this
    }

    override fun onModule(moduleId: String?): Request<T> {
        require(moduleId != null) { OptionConstants.ILLEGAL_ARGS_MESSAGE }
        this.moduleId = moduleId
        return this
    }

    private fun initSender() {
        sender?.command = command
        sender?.moduleId = moduleId
        sender?.timeout = timeout
    }

    override fun executeSync(): T {
        initSender()
        command?.requestTime = System.currentTimeMillis()
        command?.localRequestTime = System.nanoTime()
        val result = command?.let { sender?.executeSync(it) }
        if (command != null) {
            AdminServer.addMetric(command)
        }
        if (result is ExceptionHolder) throw JaffaRpcExecutionException(result.stackTrace)
        if (result is Throwable) throw JaffaRpcExecutionException(result)
        return result as T
    }

    override fun executeAsync(key: String?, listener: Class<out Callback<T>>?) {
        require(!(key == null || listener == null)) { OptionConstants.ILLEGAL_ARGS_MESSAGE }
        initSender()
        command?.callbackClass = listener.name
        command?.callbackKey = key
        command?.requestTime = System.currentTimeMillis()
        command?.localRequestTime = System.nanoTime()
        command?.asyncExpireTime = System.currentTimeMillis() + if (timeout != -1L) timeout else 1000 * 60 * 60
        log.debug("Async command {} added to finalization queue", command)
        FinalizationHelper.eventsToConsume[command?.callbackKey] = command
        if (command != null) {
            sender?.executeAsync(command)
        }
    }

    init {
        sender = try {
            Utils.currentSenderClass.getDeclaredConstructor().newInstance()
        } catch (e: Exception) {
            e.printStackTrace()
            throw JaffaRpcSystemException("Can not initialize sender!")
        }
    }
}