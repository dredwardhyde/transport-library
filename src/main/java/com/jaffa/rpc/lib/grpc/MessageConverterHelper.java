package com.jaffa.rpc.lib.grpc;

import com.google.protobuf.ByteString;
import com.jaffa.rpc.grpc.services.CallbackRequest;
import com.jaffa.rpc.grpc.services.CommandRequest;
import com.jaffa.rpc.grpc.services.CommandResponse;
import com.jaffa.rpc.lib.entities.CallbackContainer;
import com.jaffa.rpc.lib.entities.Command;
import com.jaffa.rpc.lib.security.SecurityTicket;
import com.jaffa.rpc.lib.serialization.Serializer;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings("unused")
public class MessageConverterHelper {
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
        if (StringUtils.isNotBlank(request.getToken()) && StringUtils.isNotBlank(request.getUser())) {
            SecurityTicket securityTicket = new SecurityTicket();
            securityTicket.setToken(request.getToken());
            securityTicket.setUser(request.getUser());
            command.setTicket(securityTicket);
        }
        if (Objects.nonNull(request.getMethodArgsList()) && !request.getMethodArgsList().isEmpty()) {
            String[] methodArgs = request.getMethodArgsList().toArray(new String[0]);
            command.setMethodArgs(methodArgs);
            Object[] argsObjects = new Object[methodArgs.length];
            for (int i = 0; i < methodArgs.length; i++) {
                if (request.getArgs(i).equals(ByteString.EMPTY))
                    argsObjects[i] = null;
                else
                    argsObjects[i] = Serializer.getCurrent().deserialize(request.getArgs(i).toByteArray(), Class.forName(methodArgs[i]));
            }
            command.setArgs(argsObjects);
        }
        return command;
    }

    public static CommandRequest toGRPCCommandRequest(Command command) {
        CommandRequest.Builder commandRequest = CommandRequest.newBuilder();
        commandRequest.setAsyncExpireTime(command.getAsyncExpireTime())
                .setAsyncExpireTime(command.getAsyncExpireTime())
                .setRequestTime(command.getRequestTime())
                .setLocalRequestTime(command.getLocalRequestTime());
        if (StringUtils.isNotBlank(command.getCallbackClass()))
            commandRequest.setCallbackClass(command.getCallbackClass());
        if (StringUtils.isNotBlank(command.getCallBackHost()))
            commandRequest.setCallBackHost(command.getCallBackHost());
        if (StringUtils.isNotBlank(command.getCallbackKey()))
            commandRequest.setCallbackKey(command.getCallbackKey());
        if (StringUtils.isNotBlank(command.getMethodName()))
            commandRequest.setMethodName(command.getMethodName());
        if (StringUtils.isNotBlank(command.getRqUid()))
            commandRequest.setRqUid(command.getRqUid());
        if (StringUtils.isNotBlank(command.getServiceClass()))
            commandRequest.setServiceClass(command.getServiceClass());
        if (StringUtils.isNotBlank(command.getSourceModuleId()))
            commandRequest.setSourceModuleId(command.getSourceModuleId());
        if (Objects.nonNull(command.getTicket())) {
            commandRequest.setUser(command.getTicket().getUser()).setToken(command.getTicket().getToken());
        }
        if (Objects.nonNull(command.getMethodArgs())) {
            for (int i = 0; i < command.getMethodArgs().length; i++) {
                commandRequest = commandRequest.addMethodArgs(command.getMethodArgs()[i]);
                if (Objects.nonNull(command.getArgs()[i]))
                    commandRequest.addArgs(ByteString.copyFrom(Serializer.getCurrent().serialize(command.getArgs()[i])));
                else
                    commandRequest.addArgs(ByteString.EMPTY);
            }
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
        callbackRequest.setResult(ByteString.copyFrom(Serializer.getCurrent().serializeWithClass(callbackContainer.getResult())));
        return callbackRequest.build();
    }

    public static CommandResponse toGRPCCommandResponse(Object response) {
        ByteString responseMarshalled = ByteString.copyFrom(Serializer.getCurrent().serializeWithClass(response));
        return CommandResponse.newBuilder().setResponse(responseMarshalled).build();
    }

    public static Object fromGRPCCommandResponse(CommandResponse commandResponse) {
        return Serializer.getCurrent().deserializeWithClass(commandResponse.getResponse().toByteArray());
    }
}
