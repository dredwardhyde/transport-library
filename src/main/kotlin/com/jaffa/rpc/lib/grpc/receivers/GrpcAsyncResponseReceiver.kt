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

    private lateinit var server: Server

    override fun run() {
        try {
            server = GrpcAsyncAndSyncRequestReceiver.addSecurityContext(NettyServerBuilder.forPort(Utils.callbackPort))
                    .executor(requestService).addService(CallbackServiceImpl()).build()
            server.start()
            server.awaitTermination()
        } catch (zmqStartupException: Exception) {
            log.error("Error during gRPC async response receiver startup:", zmqStartupException)
            throw JaffaRpcSystemException(zmqStartupException)
        }
        log.info("{} terminated", this.javaClass.simpleName)
    }

    override fun close() {
        try {
            server.shutdown()
            requestService.shutdown()
            log.info("gRPC async response receiver stopped")
        }catch (e: Exception){
            //No-op
        }
    }

    private class CallbackServiceImpl : CallbackServiceImplBase() {

        private val log = LoggerFactory.getLogger(CallbackServiceImpl::class.java)

        override fun execute(request: CallbackRequest, responseObserver: StreamObserver<CallbackResponse>) {
            try {
                RequestInvocationHelper.processCallbackContainer(MessageConverterHelper.fromGRPCCallbackRequest(request))
                responseObserver.also { it.onNext(CallbackResponse.newBuilder().setResponse("OK").build()) }.also { it.onCompleted() }
            } catch (exception: Exception) {
                log.error("gRPC callback execution exception", exception)
            }
        }
    }

    companion object {
        private val requestService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
    }
}