package com.jaffa.rpc.lib.grpc.receivers

import com.google.protobuf.ByteString
import com.jaffa.rpc.grpc.services.CallbackServiceGrpc
import com.jaffa.rpc.grpc.services.CommandRequest
import com.jaffa.rpc.grpc.services.CommandResponse
import com.jaffa.rpc.grpc.services.CommandServiceGrpc.CommandServiceImplBase
import com.jaffa.rpc.lib.common.OptionConstants
import com.jaffa.rpc.lib.common.RequestInvocationHelper
import com.jaffa.rpc.lib.exception.JaffaRpcSystemException
import com.jaffa.rpc.lib.grpc.MessageConverterHelper
import com.jaffa.rpc.lib.zookeeper.Utils
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.netty.GrpcSslContexts
import io.grpc.netty.NettyChannelBuilder
import io.grpc.netty.NettyServerBuilder
import io.grpc.stub.StreamObserver
import io.netty.handler.ssl.SslContextBuilder
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.tuple.Pair
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.function.Consumer

class GrpcAsyncAndSyncRequestReceiver : Runnable, Closeable {

    var server: Server? = null

    override fun run() {
        try {
            val serverBuilder = NettyServerBuilder.forPort(Utils.servicePort).also { addSecurityContext(it) }
            server = serverBuilder.executor(requestService).addService(CommandServiceImpl()).build()
                    .also { it.start() }
                    .also { it.awaitTermination() }
        } catch (grpcStartupException: Exception) {
            log.error("Error during gRPC request receiver startup:", grpcStartupException)
            throw JaffaRpcSystemException(grpcStartupException)
        }
        log.info("{} terminated", this.javaClass.simpleName)
    }

    override fun close() {
        server?.shutdown()
        asyncService.shutdown()
        requestService.shutdown()
    }

    class CommandServiceImpl : CommandServiceImplBase() {
        private fun getManagedChannel(hostAndPort: Pair<String?, Int?>?): ManagedChannel {
            return cache.computeIfAbsent(hostAndPort) { key: Pair<String?, Int?>? ->
                var channelBuilder = key?.right?.let { NettyChannelBuilder.forAddress(key.left, it) }
                channelBuilder = channelBuilder?.let { addSecurityContext(it) }
                channelBuilder!!.build()
            }
        }

        override fun execute(request: CommandRequest, responseObserver: StreamObserver<CommandResponse>?) {
            try {
                val command = MessageConverterHelper.fromGRPCCommandRequest(request)
                if (StringUtils.isNotBlank(command.callbackKey) && StringUtils.isNotBlank(command.callbackClass)) {
                    asyncService.execute {
                        try {
                            CallbackServiceGrpc.newBlockingStub(getManagedChannel(Utils.getHostAndPort(command.callBackHost, ":")))
                                    .also {
                                        it.execute(MessageConverterHelper.toGRPCCallbackRequest(
                                                RequestInvocationHelper.constructCallbackContainer(
                                                        command,
                                                        RequestInvocationHelper.invoke(command)
                                                )
                                        ))
                                    }
                        } catch (exception: Exception) {
                            log.error("Error while receiving async request", exception)
                        }
                    }
                    responseObserver?.onNext(CommandResponse.newBuilder().setResponse(ByteString.EMPTY).build())
                } else {
                    responseObserver?.onNext(MessageConverterHelper
                            .toGRPCCommandResponse(RequestInvocationHelper
                                    .getResult(RequestInvocationHelper.invoke(command))))
                }
                responseObserver?.onCompleted()
            } catch (exception: Exception) {
                log.error("Error while receiving request ", exception)
            }
        }
    }

    companion object {

        private val asyncService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())

        private val requestService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())

        private val cache: MutableMap<Pair<String?, Int?>?, ManagedChannel> = ConcurrentHashMap()

        private val log = LoggerFactory.getLogger(GrpcAsyncAndSyncRequestReceiver::class.java)

        fun shutDownChannels() {
            cache.values.forEach(Consumer { x: ManagedChannel -> if (!x.isShutdown) x.shutdownNow() })
            log.info("All gRPC async reply channels were terminated")
        }

        fun addSecurityContext(serverBuilder: NettyServerBuilder): NettyServerBuilder {
            return try {
                if (System.getProperty(OptionConstants.GRPC_USE_SSL, "false").toBoolean()) {
                    serverBuilder.sslContext(
                            GrpcSslContexts.configure(
                                    SslContextBuilder.forServer(
                                            File(Utils.getRequiredOption(OptionConstants.GRPC_SSL_SERVER_STORE_LOCATION)),
                                            File(Utils.getRequiredOption(OptionConstants.GRPC_SSL_SERVER_KEY_LOCATION))
                                    )
                            ).build()
                    )
                } else {
                    serverBuilder
                }
            } catch (sslException: Exception) {
                log.error("Exception occurred while creating SSL context for gRPC", sslException)
                throw JaffaRpcSystemException(sslException)
            }
        }

        fun addSecurityContext(channelBuilder: NettyChannelBuilder): NettyChannelBuilder {
            return try {
                if (System.getProperty(OptionConstants.GRPC_USE_SSL, "false").toBoolean()) {
                    channelBuilder.sslContext(
                            GrpcSslContexts.configure(
                                    SslContextBuilder.forClient().keyManager(
                                            File(Utils.getRequiredOption(OptionConstants.GRPC_SSL_CLIENT_KEYSTORE_LOCATION)),
                                            File(Utils.getRequiredOption(OptionConstants.GRPC_SSL_CLIENT_KEY_LOCATION))
                                    )
                            )
                                    .trustManager(File(Utils.getRequiredOption(OptionConstants.GRPC_SSL_CLIENT_TRUSTSTORE_LOCATION)))
                                    .build()
                    ).useTransportSecurity()
                } else {
                    channelBuilder.usePlaintext()
                }
            } catch (sslException: Exception) {
                log.error("Exception occurred while creating SSL context for gRPC", sslException)
                throw JaffaRpcSystemException(sslException)
            }
        }
    }
}