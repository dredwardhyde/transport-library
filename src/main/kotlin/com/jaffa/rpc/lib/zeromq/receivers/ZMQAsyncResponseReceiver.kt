package com.jaffa.rpc.lib.zeromq.receivers

import com.jaffa.rpc.lib.common.OptionConstants
import com.jaffa.rpc.lib.common.RequestInvocationHelper
import com.jaffa.rpc.lib.entities.CallbackContainer
import com.jaffa.rpc.lib.exception.JaffaRpcSystemException
import com.jaffa.rpc.lib.serialization.Serializer
import com.jaffa.rpc.lib.zeromq.CurveUtils
import com.jaffa.rpc.lib.zeromq.ZeroMqRequestSender
import com.jaffa.rpc.lib.zookeeper.Utils
import org.slf4j.LoggerFactory
import org.zeromq.SocketType
import org.zeromq.ZAuth
import org.zeromq.ZContext
import org.zeromq.ZMQ
import org.zeromq.ZMQException
import zmq.ZError
import java.io.Closeable
import java.io.IOException
import java.net.UnknownHostException

class ZMQAsyncResponseReceiver : Runnable, Closeable {

    private val log = LoggerFactory.getLogger(ZMQAsyncResponseReceiver::class.java)

    private var context: ZContext? = null
    private var socket: ZMQ.Socket? = null
    private var auth: ZAuth? = null
    override fun run() {
        while (!Thread.currentThread().isInterrupted) {
            try {
                val bytes = socket?.recv()
                socket?.send(byteArrayOf(4))
                if (bytes != null && bytes.size == 1 && bytes[0] == 7.toByte()) {
                    ZMQAsyncAndSyncRequestReceiver.destroySocketAndContext(
                        context,
                        socket,
                        ZMQAsyncResponseReceiver::class.java
                    )
                    break
                }
                val callbackContainer = Serializer.current.deserialize(bytes, CallbackContainer::class.java)
                RequestInvocationHelper.processCallbackContainer(callbackContainer)
            } catch (recvTerminationException: ZMQException) {
                ZMQAsyncAndSyncRequestReceiver.checkZMQExceptionAndThrow(recvTerminationException)
            } catch (recvTerminationException: ZError.IOException) {
                ZMQAsyncAndSyncRequestReceiver.checkZMQExceptionAndThrow(recvTerminationException)
            } catch (callbackExecutionException: Exception) {
                log.error("ZMQ callback execution exception", callbackExecutionException)
            }
        }
        log.info("{} terminated", this.javaClass.simpleName)
    }

    @Throws(UnknownHostException::class)
    override fun close() {
        sendKillMessageToSocket(Utils.zeroMQCallbackBindAddress)
        if (System.getProperty(OptionConstants.ZMQ_CURVE_ENABLED, false.toString()).toBoolean()) {
            try {
                auth?.close()
            } catch (ioException: IOException) {
                log.error("Error while closing ZeroMQ context", ioException)
            }
        }
        log.info("ZMQAsyncResponseReceiver closed")
    }

    companion object {
        private val log = LoggerFactory.getLogger(ZMQAsyncResponseReceiver::class.java)

        fun sendKillMessageToSocket(address: String?) {
            val contextToClose = ZContext(1)
            try {
                contextToClose.createSocket(SocketType.REQ).use { socketClose ->
                    ZeroMqRequestSender.addCurveKeysToSocket(socketClose, OptionConstants.MODULE_ID)
                    socketClose.linger = 0
                    socketClose.connect("tcp://$address")
                    socketClose.send(byteArrayOf(7), 0)
                    socketClose.receiveTimeOut = 1
                    socketClose.recv(0)
                }
            } catch (e: Throwable) {
                log.error("Error while sending kill message", e)
            }
            contextToClose.close()
            log.info("Kill message sent to {} receiver", address)
        }
    }

    init {
        try {
            context = ZContext(10)
            context?.linger = 0
            if (System.getProperty(OptionConstants.ZMQ_CURVE_ENABLED, false.toString()).toBoolean()) {
                auth = ZAuth(context)
                auth?.configureCurve(Utils.getRequiredOption(OptionConstants.ZMQ_CLIENT_DIR))
            }
            socket = context?.createSocket(SocketType.REP)
            CurveUtils.makeSocketSecure(socket)
            socket?.bind("tcp://" + Utils.zeroMQCallbackBindAddress)
        } catch (zmqStartupException: UnknownHostException) {
            ZMQAsyncResponseReceiver.log.error("Error during ZeroMQ response receiver startup:", zmqStartupException)
            throw JaffaRpcSystemException(zmqStartupException)
        }
    }
}