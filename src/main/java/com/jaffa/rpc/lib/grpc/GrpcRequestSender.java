package com.jaffa.rpc.lib.grpc;

import com.google.protobuf.ByteString;
import com.jaffa.rpc.grpc.services.CommandResponse;
import com.jaffa.rpc.grpc.services.CommandServiceGrpc;
import com.jaffa.rpc.lib.entities.Command;
import com.jaffa.rpc.lib.entities.Protocol;
import com.jaffa.rpc.lib.exception.JaffaRpcExecutionException;
import com.jaffa.rpc.lib.exception.JaffaRpcExecutionTimeoutException;
import com.jaffa.rpc.lib.exception.JaffaRpcNoRouteException;
import com.jaffa.rpc.lib.grpc.receivers.GrpcAsyncAndSyncRequestReceiver;
import com.jaffa.rpc.lib.request.Sender;
import com.jaffa.rpc.lib.zookeeper.Utils;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NettyChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
public class GrpcRequestSender extends Sender {

    private static final Map<Pair<String, Integer>, ManagedChannel> cache = new ConcurrentHashMap<>();

    public static void shutDownChannels() {
        cache.values().forEach(x -> {
            if (!x.isShutdown()) x.shutdownNow();
        });
        log.info("All gRPC channels were terminated");
    }

    @Override
    public byte[] executeSync(byte[] message) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void executeAsync(byte[] message) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object executeSync(Command command) {
        try {
            ManagedChannel channel = getManagedChannel();
            CommandServiceGrpc.CommandServiceBlockingStub stub = CommandServiceGrpc.newBlockingStub(channel);
            int totalTimeout = (int) (this.timeout == -1 ? 1000 * 60 * 60 : this.timeout);
            CommandResponse commandResponse = stub.withDeadlineAfter(totalTimeout, TimeUnit.MILLISECONDS).execute(MessageConverterHelper.toGRPCCommandRequest(command));
            return MessageConverterHelper.fromGRPCCommandResponse(commandResponse);
        } catch (StatusRuntimeException statusRuntimeException) {
            processStatusException(statusRuntimeException);
        } catch (Exception exception) {
            throw new JaffaRpcExecutionException(exception);
        }
        return null;
    }

    private ManagedChannel getManagedChannel() {
        Pair<String, Integer> hostAndPort = Utils.getHostAndPort(Utils.getHostForService(command.getServiceClass(), moduleId, Protocol.GRPC).getLeft(), ":");
        return cache.computeIfAbsent(hostAndPort, key -> {
            NettyChannelBuilder channelBuilder = NettyChannelBuilder.forAddress(key.getLeft(), key.getRight());
            channelBuilder = GrpcAsyncAndSyncRequestReceiver.addSecurityContext(channelBuilder);
            return channelBuilder.build();
        });
    }

    private void processStatusException(StatusRuntimeException statusRuntimeException) {
        if (statusRuntimeException.getStatus().getCode() == Status.DEADLINE_EXCEEDED.getCode())
            throw new JaffaRpcExecutionTimeoutException();
        else if (statusRuntimeException.getStatus().getCode() == Status.UNAVAILABLE.getCode()) {
            throw new JaffaRpcNoRouteException(command.getServiceClass(), Protocol.GRPC);
        } else
            throw new JaffaRpcExecutionException(statusRuntimeException);
    }

    @Override
    public void executeAsync(Command command) {
        try {
            ManagedChannel channel = getManagedChannel();
            CommandServiceGrpc.CommandServiceBlockingStub stub = CommandServiceGrpc.newBlockingStub(channel);
            CommandResponse response = stub.execute(MessageConverterHelper.toGRPCCommandRequest(command));
            if (!response.getResponse().equals(ByteString.EMPTY))
                throw new JaffaRpcExecutionException("Wrong value returned after async callback processing!");
        } catch (StatusRuntimeException statusRuntimeException) {
            processStatusException(statusRuntimeException);
        } catch (Exception exception) {
            throw new JaffaRpcExecutionException(exception);
        }
    }
}