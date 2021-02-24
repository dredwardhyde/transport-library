package com.jaffa.rpc.lib.grpc

import com.google.protobuf.ByteString
import com.jaffa.rpc.grpc.services.CommandServiceGrpc
import com.jaffa.rpc.lib.entities.Command
import com.jaffa.rpc.lib.entities.Protocol
import com.jaffa.rpc.lib.exception.JaffaRpcExecutionException
import com.jaffa.rpc.lib.exception.JaffaRpcExecutionTimeoutException
import com.jaffa.rpc.lib.exception.JaffaRpcNoRouteException
import com.jaffa.rpc.lib.grpc.receivers.GrpcAsyncAndSyncRequestReceiver
import com.jaffa.rpc.lib.request.Sender
import com.jaffa.rpc.lib.zookeeper.Utils
import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.netty.NettyChannelBuilder
import org.apache.commons.lang3.tuple.Pair
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

class GrpcRequestSender : Sender() {

    public override fun executeSync(message: ByteArray?): ByteArray? {
        throw UnsupportedOperationException()
    }

    public override fun executeAsync(message: ByteArray?) {
        throw UnsupportedOperationException()
    }

    override fun executeSync(command: Command): Any? {
        try {
            val channel = managedChannel
            val stub = CommandServiceGrpc.newBlockingStub(channel)
            val totalTimeout = (if (timeout == -1L) 1000 * 60 * 60 else timeout).toInt()
            val commandResponse = stub.withDeadlineAfter(totalTimeout.toLong(), TimeUnit.MILLISECONDS)
                .execute(MessageConverterHelper.toGRPCCommandRequest(command))
            return MessageConverterHelper.fromGRPCCommandResponse(commandResponse)
        } catch (statusRuntimeException: StatusRuntimeException) {
            processStatusException(statusRuntimeException)
        } catch (jaffaRpcNoRouteException: JaffaRpcNoRouteException) {
            throw jaffaRpcNoRouteException
        } catch (exception: Exception) {
            throw JaffaRpcExecutionException(exception)
        }
        return null
    }

    private val managedChannel: ManagedChannel
        get() {
            val hostAndPort =
                Utils.getHostAndPort(Utils.getHostForService(command?.serviceClass, moduleId, Protocol.GRPC).left, ":")
            return cache.computeIfAbsent(hostAndPort) { key: Pair<String?, Int?>? ->
                GrpcAsyncAndSyncRequestReceiver.addSecurityContext(
                    NettyChannelBuilder.forAddress(
                        key?.left,
                        key?.right!!
                    )
                ).build()
            }
        }

    private fun processStatusException(statusRuntimeException: StatusRuntimeException) {
        if (statusRuntimeException.status.code == Status.DEADLINE_EXCEEDED.code) throw JaffaRpcExecutionTimeoutException() else if (statusRuntimeException.status.code == Status.UNAVAILABLE.code) {
            throw JaffaRpcNoRouteException(command?.serviceClass, Protocol.GRPC)
        } else throw JaffaRpcExecutionException(statusRuntimeException)
    }

    override fun executeAsync(command: Command) {
        try {
            val channel = managedChannel
            val stub = CommandServiceGrpc.newBlockingStub(channel)
            val response = stub.execute(MessageConverterHelper.toGRPCCommandRequest(command))
            if (response.response != ByteString.EMPTY) throw JaffaRpcExecutionException("Wrong value returned after async callback processing!")
        } catch (statusRuntimeException: StatusRuntimeException) {
            processStatusException(statusRuntimeException)
        } catch (jaffaRpcNoRouteException: JaffaRpcNoRouteException) {
            throw jaffaRpcNoRouteException
        } catch (exception: Exception) {
            throw JaffaRpcExecutionException(exception)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(GrpcRequestSender::class.java)
        private val cache: MutableMap<Pair<String?, Int?>?, ManagedChannel> = ConcurrentHashMap()
        fun shutDownChannels() {
            cache.values.forEach(Consumer { x: ManagedChannel -> if (!x.isShutdown) x.shutdownNow() })
            log.info("All gRPC channels were terminated")
        }
    }
}