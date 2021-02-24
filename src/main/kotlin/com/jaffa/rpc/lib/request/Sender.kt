package com.jaffa.rpc.lib.request

import com.jaffa.rpc.lib.entities.Command
import com.jaffa.rpc.lib.exception.JaffaRpcExecutionTimeoutException
import com.jaffa.rpc.lib.serialization.Serializer

abstract class Sender {
    var timeout: Long = -1
    var moduleId: String? = null
    var command: Command? = null
    protected abstract fun executeSync(message: ByteArray?): ByteArray?
    protected abstract fun executeAsync(message: ByteArray?)
    open fun executeSync(command: Command): Any? {
        val out = Serializer.current.serialize(command)
        val response = executeSync(out) ?: throw JaffaRpcExecutionTimeoutException()
        return Serializer.current.deserializeWithClass(response)
    }

    open fun executeAsync(command: Command) {
        executeAsync(Serializer.current.serialize(command))
    }
}