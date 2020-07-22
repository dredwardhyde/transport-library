package com.jaffa.rpc.lib.grpc.receivers;

import com.jaffa.rpc.grpc.services.CallbackRequest;
import com.jaffa.rpc.grpc.services.CallbackResponse;
import com.jaffa.rpc.grpc.services.CallbackServiceGrpc;
import com.jaffa.rpc.lib.common.RequestInvocationHelper;
import com.jaffa.rpc.lib.entities.CallbackContainer;
import com.jaffa.rpc.lib.exception.JaffaRpcExecutionException;
import com.jaffa.rpc.lib.exception.JaffaRpcSystemException;
import com.jaffa.rpc.lib.grpc.MessageConverterHelper;
import com.jaffa.rpc.lib.zookeeper.Utils;
import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class GrpcAsyncResponseReceiver implements Runnable, Closeable {

    private static final ExecutorService requestService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private Server server;

    @Override
    public void run() {
        try {
            NettyServerBuilder serverBuilder = NettyServerBuilder.forPort(Utils.getCallbackPort());
            serverBuilder = GrpcAsyncAndSyncRequestReceiver.addSecurityContext(serverBuilder);
            server = serverBuilder.executor(requestService).addService(new CallbackServiceImpl()).build();
            server.start();
            server.awaitTermination();
        } catch (Exception zmqStartupException) {
            log.error("Error during gRPC async response receiver startup:", zmqStartupException);
            throw new JaffaRpcSystemException(zmqStartupException);
        }
        log.info("{} terminated", this.getClass().getSimpleName());
    }

    @Override
    public void close() {
        server.shutdown();
        requestService.shutdown();
        log.info("gRPC async response receiver stopped");
    }

    private static class CallbackServiceImpl extends CallbackServiceGrpc.CallbackServiceImplBase {
        @Override
        public void execute(CallbackRequest request, StreamObserver<CallbackResponse> responseObserver) {
            try {
                CallbackContainer callbackContainer = MessageConverterHelper.fromGRPCCallbackRequest(request);
                RequestInvocationHelper.processCallbackContainer(callbackContainer);
                responseObserver.onNext(CallbackResponse.newBuilder().setResponse("OK").build());
                responseObserver.onCompleted();
            } catch (Exception exception) {
                log.error("gRPC callback execution exception", exception);
                throw new JaffaRpcExecutionException(exception);
            }
        }
    }
}