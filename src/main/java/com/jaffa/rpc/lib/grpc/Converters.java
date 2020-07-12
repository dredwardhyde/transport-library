package com.jaffa.rpc.lib.grpc;

import com.google.protobuf.ByteString;
import com.jaffa.rpc.grpc.CallbackRequest;
import com.jaffa.rpc.grpc.CallbackResponse;
import com.jaffa.rpc.grpc.CommandRequest;
import com.jaffa.rpc.grpc.CommandResponse;
import com.jaffa.rpc.lib.entities.CallbackContainer;
import com.jaffa.rpc.lib.entities.Command;
import com.jaffa.rpc.lib.security.SecurityTicket;
import com.jaffa.rpc.lib.serialization.Serializer;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Objects;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings("unused")
public class Converters {
    public static Command fromGRPCCommandRequest(CommandRequest request) throws ClassNotFoundException {
        Command command = new Command();
        command.setCallBackHost(request.getCallBackHost());
        command.setCallbackClass(request.getCallbackClass());
        command.setAsyncExpireTime(request.getAsyncExpireTime());
        command.setMethodName(request.getMethodName());
        command.setCallbackKey(request.getCallbackKey());
        command.setLocalRequestTime(request.getLocalRequestTime());
        command.setRequestTime(request.getRequestTime());
        command.setRqUid(request.getRqUid());
        command.setSourceModuleId(request.getSourceModuleId());
        command.setServiceClass(request.getServiceClass());
        if (Objects.nonNull(request.getToken()) && Objects.nonNull(request.getUser())) {
            SecurityTicket securityTicket = new SecurityTicket();
            securityTicket.setToken(request.getToken());
            securityTicket.setUser(request.getUser());
            command.setTicket(securityTicket);
        }
        String[] methodArgs = request.getMethodArgsList().toArray(new String[0]);
        command.setMethodArgs(methodArgs);
        Object[] argsObjects = new Object[request.getArgsCount()];
        for (int i = 0; i < request.getArgsCount(); i++) {
            argsObjects[i] = Serializer.getCurrent().deserialize(request.getArgs(i).toByteArray(), Class.forName(methodArgs[i]));
        }
        command.setArgs(argsObjects);
        return command;
    }

    public static CommandRequest toGRPCCommandRequest(Command command) {
        CommandRequest.Builder commandRequest = CommandRequest.newBuilder();
        commandRequest = commandRequest.setAsyncExpireTime(command.getAsyncExpireTime())
                .setLocalRequestTime(command.getLocalRequestTime())
                .setCallbackClass(command.getCallbackClass())
                .setCallBackHost(command.getCallBackHost())
                .setCallbackKey(command.getCallbackKey())
                .setMethodName(command.getMethodName())
                .setRequestTime(command.getRequestTime())
                .setRqUid(command.getRqUid())
                .setServiceClass(command.getServiceClass())
                .setSourceModuleId(command.getSourceModuleId())
                .setAsyncExpireTime(command.getAsyncExpireTime());
        if (Objects.nonNull(command.getTicket())) {
            commandRequest = commandRequest.setUser(command.getTicket().getUser()).setToken(command.getTicket().getToken());
        }
        for (int i = 0; i < command.getMethodArgs().length; i++) {
            commandRequest = commandRequest.setMethodArgs(i, command.getMethodArgs()[i]);
        }
        for (int i = 0; i < command.getMethodArgs().length; i++) {
            if (Objects.nonNull(command.getArgs()[i]))
                commandRequest = commandRequest.setArgs(i, ByteString.copyFrom(Serializer.getCurrent().serialize(command.getArgs()[i])));
        }
        return commandRequest.build();
    }

    public static CallbackContainer fromGRPCCallbackRequest(CallbackRequest callbackRequest) {
        CallbackContainer callbackContainer = new CallbackContainer();
        callbackContainer.setKey(callbackRequest.getKey());
        callbackContainer.setListener(callbackRequest.getListener());
        callbackContainer.setResultClass(callbackRequest.getResultClass());
        if (Objects.nonNull(callbackRequest.getResult()) && !callbackRequest.getResult().isEmpty()) {
            callbackContainer.setResult(Serializer.getCurrent().deserializeWithClass(callbackRequest.getResult().toByteArray()));
        }
        return callbackContainer;
    }

    public static CallbackRequest toGRPCCallbackRequest(CallbackContainer callbackContainer) {
        CallbackRequest.Builder callbackRequest = CallbackRequest.newBuilder();
        callbackRequest.setKey(callbackContainer.getKey());
        callbackRequest.setListener(callbackContainer.getListener());
        callbackRequest.setResultClass(callbackContainer.getResultClass());
        if (Objects.nonNull(callbackContainer.getResult())) {
            callbackRequest.setResult(ByteString.copyFrom(Serializer.getCurrent().serializeWithClass(callbackRequest.getResult())));
        }
        return callbackRequest.build();
    }

    public static CallbackResponse toGRPCCallbackResponse(String response) {
        return CallbackResponse.newBuilder().setResponse(response).build();
    }

    public static CommandResponse toGRPCCommandResponse(Object response) {
        ByteString responseMarshalled = Objects.nonNull(response) ? ByteString.copyFrom(Serializer.getCurrent().serializeWithClass(response)) : null;
        return CommandResponse.newBuilder().setResponse(responseMarshalled).build();
    }

    public static Object fromGRPCCommandResponse(CommandResponse commandResponse){
        return Serializer.getCurrent().deserializeWithClass(commandResponse.getResponse().toByteArray());
    }
}
