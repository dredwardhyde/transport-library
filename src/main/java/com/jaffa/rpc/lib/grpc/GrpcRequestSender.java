package com.jaffa.rpc.lib.grpc;

import com.jaffa.rpc.grpc.services.CommandResponse;
import com.jaffa.rpc.grpc.services.CommandServiceGrpc;
import com.jaffa.rpc.lib.entities.Command;
import com.jaffa.rpc.lib.entities.Protocol;
import com.jaffa.rpc.lib.exception.JaffaRpcExecutionException;
import com.jaffa.rpc.lib.exception.JaffaRpcExecutionTimeoutException;
import com.jaffa.rpc.lib.request.Sender;
import com.jaffa.rpc.lib.zookeeper.Utils;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import java.util.concurrent.TimeUnit;

public class GrpcRequestSender extends Sender {

    @Override
    protected byte[] executeSync(byte[] message) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void executeAsync(byte[] message) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object executeSync(Command command) {
        ManagedChannel channel = getManagedChannel();
        CommandServiceGrpc.CommandServiceBlockingStub stub = CommandServiceGrpc.newBlockingStub(channel);
        int totalTimeout = (int) (this.timeout == -1 ? 1000 * 60 * 60 : this.timeout);
        try {
            CommandResponse commandResponse = stub.withDeadlineAfter(totalTimeout, TimeUnit.MILLISECONDS).execute(Converters.toGRPCCommandRequest(command));
            return Converters.fromGRPCCommandResponse(commandResponse);
        } catch (StatusRuntimeException statusRuntimeException) {
            if (statusRuntimeException.getStatus() == Status.DEADLINE_EXCEEDED)
                throw new JaffaRpcExecutionTimeoutException();
            else
                throw new JaffaRpcExecutionException(statusRuntimeException);
        } finally {
            channel.shutdownNow();
        }
    }

    private ManagedChannel getManagedChannel() {
        String[] hostAndPort = Utils.getHostForService(command.getServiceClass(), moduleId, Protocol.GRPC).getLeft().split(":");
        return ManagedChannelBuilder.forAddress(hostAndPort[0], Integer.parseInt(hostAndPort[1])).usePlaintext().build();
    }

    @Override
    public void executeAsync(Command command) {
        ManagedChannel channel = getManagedChannel();
        CommandServiceGrpc.CommandServiceBlockingStub stub = CommandServiceGrpc.newBlockingStub(channel);
        stub.execute(Converters.toGRPCCommandRequest(command));
        channel.shutdownNow();
    }
}