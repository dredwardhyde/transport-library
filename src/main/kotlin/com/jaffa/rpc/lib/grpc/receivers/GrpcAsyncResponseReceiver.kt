package com.jaffa.rpc.lib.grpc.receivers

import com.jaffa.rpc.grpc.services.CallbackRequest
import com.jaffa.rpc.grpc.services.CallbackResponse
import com.jaffa.rpc.grpc.services.CallbackServiceGrpc.CallbackServiceImplBase
import com.jaffa.rpc.lib.common.RequestInvocationHelper
import com.jaffa.rpc.lib.exception.JaffaRpcSystemException
import com.jaffa.rpc.lib.grpc.MessageConverterHelper
import com.jaffa.rpc.lib.zookeeper.Utils
import io.grpc.Server
import io.grpc.netty.NettyServerBuilder
import io.grpc.stub.StreamObserver
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.util.concurrent.Executors

class GrpcAsyncResponseReceiver : Runnable, Closeable {
    private val log = LoggerFactory.getLogger(GrpcAsyncResponseReceiver::class.java)
    private var server: Server? = null
    override fun run() {
        try {
            var serverBuilder = NettyServerBuilder.forPort(Utils.callbackPort)
            serverBuilder = GrpcAsyncAndSyncRequestReceiver.addSecurityContext(serverBuilder)
            server = serverBuilder.executor(requestService).addService(CallbackServiceImpl()).build()
            server?.start()
            server?.awaitTermination()
        } catch (zmqStartupException: Exception) {
            log.error("Error during gRPC async response receiver startup:", zmqStartupException)
            throw JaffaRpcSystemException(zmqStartupException)
        }
        log.info("{} terminated", this.javaClass.simpleName)
    }

    override fun close() {
        server?.shutdown()
        requestService.shutdown()
        log.info("gRPC async response receiver stopped")
    }

    private class CallbackServiceImpl : CallbackServiceImplBase() {
        private val log = LoggerFactory.getLogger(CallbackServiceImpl::class.java)

        override fun execute(request: CallbackRequest, responseObserver: StreamObserver<CallbackResponse>) {
            try {
                val callbackContainer = MessageConverterHelper.fromGRPCCallbackRequest(request)
                RequestInvocationHelper.processCallbackContainer(callbackContainer)
                responseObserver.onNext(CallbackResponse.newBuilder().setResponse("OK").build())
                responseObserver.onCompleted()
            } catch (exception: Exception) {
                log.error("gRPC callback execution exception", exception)
            }
        }
    }

    companion object {
        private val requestService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
    }
}