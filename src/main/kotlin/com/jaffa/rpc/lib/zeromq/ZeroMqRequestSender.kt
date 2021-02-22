package com.jaffa.rpc.lib.zeromq

import com.jaffa.rpc.lib.common.OptionConstants
import com.jaffa.rpc.lib.entities.Protocol
import com.jaffa.rpc.lib.exception.JaffaRpcExecutionException
import com.jaffa.rpc.lib.request.Sender
import com.jaffa.rpc.lib.zookeeper.Utils
import org.slf4j.LoggerFactory
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ

class ZeroMqRequestSender : Sender() {

    private val log = LoggerFactory.getLogger(ZeroMqRequestSender::class.java)

    override fun executeSync(message: ByteArray?): ByteArray? {
        val start = System.currentTimeMillis()
        var response: ByteArray?
        context.createSocket(SocketType.REQ).use { socket ->
            val hostAndModuleId = Utils.getHostForService(command?.serviceClass, moduleId, Protocol.ZMQ)
            addCurveKeysToSocket(socket, hostAndModuleId.right)
            socket.linger = 0
            socket.connect("tcp://" + hostAndModuleId.left)
            socket.send(message, 0)
            socket.receiveTimeOut = (if (timeout == -1L) 1000 * 60 * 60 else timeout).toInt()
            response = socket.recv(0)
        }
        log.debug(">>>>>> Executed sync request {} in {} ms", command?.rqUid, System.currentTimeMillis() - start)
        return response
    }

    override fun executeAsync(message: ByteArray?) {
        val start = System.currentTimeMillis()
        context.createSocket(SocketType.REQ).use { socket ->
            val hostAndModuleId = Utils.getHostForService(command?.serviceClass, moduleId, Protocol.ZMQ)
            addCurveKeysToSocket(socket, hostAndModuleId.right)
            socket.linger = 0
            socket.connect("tcp://" + hostAndModuleId.left)
            socket.send(message, 0)
            socket.recv(0)
        }
        log.debug(">>>>>> Executed async request {} in {} ms", command?.rqUid, System.currentTimeMillis() - start)
    }

    companion object {
        val context = ZContext(10)

        @kotlin.jvm.JvmStatic
        fun addCurveKeysToSocket(socket: ZMQ.Socket?, moduleId: String?) {
            if (System.getProperty(OptionConstants.ZMQ_CURVE_ENABLED, false.toString()).toBoolean()) {
                socket?.curvePublicKey = CurveUtils.serverPublicKey?.toByteArray()
                socket?.curveSecretKey = CurveUtils.serverSecretKey?.toByteArray()
                val clientPublicKey = CurveUtils.getClientPublicKey(moduleId)
                        ?: throw JaffaRpcExecutionException("No Curve client key was provided for module.id $moduleId")
                socket?.curveServerKey = clientPublicKey.toByteArray(Charsets.UTF_8)
            }
        }
    }
}