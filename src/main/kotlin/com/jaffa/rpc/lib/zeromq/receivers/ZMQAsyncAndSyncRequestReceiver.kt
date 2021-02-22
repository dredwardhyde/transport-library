package com.jaffa.rpc.lib.zeromq.receivers

import com.jaffa.rpc.lib.common.OptionConstants
import com.jaffa.rpc.lib.common.RequestInvocationHelper
import com.jaffa.rpc.lib.entities.Command
import com.jaffa.rpc.lib.exception.JaffaRpcSystemException
import com.jaffa.rpc.lib.serialization.Serializer
import com.jaffa.rpc.lib.zeromq.CurveUtils
import com.jaffa.rpc.lib.zeromq.ZeroMqRequestSender
import com.jaffa.rpc.lib.zeromq.receivers.ZMQAsyncAndSyncRequestReceiver
import com.jaffa.rpc.lib.zookeeper.Utils
import org.slf4j.LoggerFactory
import org.zeromq.*
import zmq.ZError
import java.io.Closeable
import java.io.IOException
import java.lang.Boolean
import java.net.UnknownHostException
import java.util.concurrent.Executors

class ZMQAsyncAndSyncRequestReceiver : Runnable, Closeable {

    private val log = LoggerFactory.getLogger(ZMQAsyncAndSyncRequestReceiver::class.java)

    private var context: ZContext? = null
    private var socket: ZMQ.Socket? = null
    private var auth: ZAuth? = null
    override fun run() {
        while (!Thread.currentThread().isInterrupted) {
            try {
                val bytes = socket?.recv()
                if (bytes != null && bytes.size == 1 && bytes[0] == 7.toByte()) {
                    destroySocketAndContext(context, socket, ZMQAsyncAndSyncRequestReceiver::class.java)
                    break
                }
                val command = Serializer.current?.deserialize(bytes, Command::class.java)
                if (command?.callbackKey != null && command.callbackClass != null) {
                    socket?.send("OK")
                    val runnable = Runnable {
                        try {
                            val result = RequestInvocationHelper.invoke(command)
                            val serializedResponse = Serializer.current?.serialize(RequestInvocationHelper.constructCallbackContainer(command, result))
                            log.debug("Async response to request {} is ready", command.callbackKey)
                            val socketAsync = context?.createSocket(SocketType.REQ)
                            ZeroMqRequestSender.addCurveKeysToSocket(socketAsync, command.sourceModuleId)
                            socketAsync?.connect("tcp://" + command.callBackHost)
                            socketAsync?.send(serializedResponse, 0)
                            socketAsync?.recv(0)
                            context?.destroySocket(socketAsync)
                        } catch (exception: Exception) {
                            log.error("Error while receiving async request", exception)
                        }
                    }
                    service.execute(runnable)
                } else {
                    val result = command?.let { RequestInvocationHelper.invoke(it) }
                    val serializedResponse = Serializer.current?.serializeWithClass(RequestInvocationHelper.getResult(result))
                    socket?.send(serializedResponse)
                }
            } catch (recvTerminationException: ZMQException) {
                checkZMQExceptionAndThrow(recvTerminationException)
            } catch (recvTerminationException: ZError.IOException) {
                checkZMQExceptionAndThrow(recvTerminationException)
            } catch (exception: Exception) {
                log.error("Error while receiving sync request", exception)
            }
        }
        log.info("{} terminated", this.javaClass.simpleName)
    }

    @Throws(UnknownHostException::class)
    override fun close() {
        ZMQAsyncResponseReceiver.sendKillMessageToSocket(Utils.zeroMQBindAddress)
        if (Boolean.parseBoolean(System.getProperty(OptionConstants.ZMQ_CURVE_ENABLED, false.toString()))) {
            try {
                auth?.close()
            } catch (ioException: IOException) {
                log.error("Error while closing ZeroMQ context", ioException)
            }
        }
        service.shutdownNow()
        log.info("ZMQAsyncAndSyncRequestReceiver closed")
    }

    companion object {

        private val log = LoggerFactory.getLogger(ZMQAsyncAndSyncRequestReceiver::class.java)

        private val service = Executors.newFixedThreadPool(3)

        @kotlin.jvm.JvmStatic
        fun checkZMQExceptionAndThrow(recvTerminationException: Exception) {
            if (!recvTerminationException.message?.contains("Errno 4")!! && !recvTerminationException.message?.contains("156384765")!!) {
                log.error("General ZMQ exception", recvTerminationException)
                throw JaffaRpcSystemException(recvTerminationException)
            }
        }

        @kotlin.jvm.JvmStatic
        fun destroySocketAndContext(context: ZContext?, socket: ZMQ.Socket?, source: Class<*>) {
            context?.destroySocket(socket)
            log.info("{} socket destroyed", source.simpleName)
            context?.destroy()
            log.info("{} context destroyed", source.simpleName)
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
            socket?.bind("tcp://" + Utils.zeroMQBindAddress)
        } catch (zmqStartupException: Exception) {
            ZMQAsyncAndSyncRequestReceiver.log.error("Error during ZeroMQ request receiver startup:", zmqStartupException)
            throw JaffaRpcSystemException(zmqStartupException)
        }
    }
}