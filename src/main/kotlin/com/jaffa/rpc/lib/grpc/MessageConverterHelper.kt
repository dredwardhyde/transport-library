package com.jaffa.rpc.lib.grpc

import com.google.protobuf.ByteString
import com.jaffa.rpc.grpc.services.CallbackRequest
import com.jaffa.rpc.grpc.services.CommandRequest
import com.jaffa.rpc.grpc.services.CommandResponse
import com.jaffa.rpc.lib.entities.CallbackContainer
import com.jaffa.rpc.lib.entities.Command
import com.jaffa.rpc.lib.security.SecurityTicket
import com.jaffa.rpc.lib.serialization.Serializer
import org.apache.commons.lang3.StringUtils

object MessageConverterHelper {
    @Throws(ClassNotFoundException::class)
    fun fromGRPCCommandRequest(request: CommandRequest): Command {
        val command = Command()
        command.callBackHost = request.callBackHost
        command.callbackClass = request.callbackClass
        command.asyncExpireTime = request.asyncExpireTime
        command.methodName = request.methodName
        command.callbackKey = request.callbackKey
        command.localRequestTime = request.localRequestTime
        command.requestTime = request.requestTime
        command.rqUid = request.rqUid
        command.sourceModuleId = request.sourceModuleId
        command.serviceClass = request.serviceClass
        if (StringUtils.isNotBlank(request.token) && StringUtils.isNotBlank(request.user)) {
            val securityTicket = SecurityTicket()
            securityTicket.token = request.token
            securityTicket.user = request.user
            command.ticket = securityTicket
        }
        if (request.methodArgsList != null && !request.methodArgsList.isEmpty()) {
            val methodArgs = request.methodArgsList.toTypedArray()
            command.methodArgs = methodArgs
            val argsObjects = arrayOfNulls<Any>(methodArgs.size)
            for (i in methodArgs.indices) {
                if (request.getArgs(i) == ByteString.EMPTY) argsObjects[i] = null else argsObjects[i] = Serializer.current?.deserialize(request.getArgs(i).toByteArray(), Class.forName(methodArgs[i]))
            }
            command.args = argsObjects
        }
        return command
    }

    @kotlin.jvm.JvmStatic
    fun toGRPCCommandRequest(command: Command): CommandRequest {
        var commandRequest = CommandRequest.newBuilder()
        commandRequest.setAsyncExpireTime(command.asyncExpireTime)
                .setAsyncExpireTime(command.asyncExpireTime)
                .setRequestTime(command.requestTime).localRequestTime = command.localRequestTime
        if (StringUtils.isNotBlank(command.callbackClass)) commandRequest.callbackClass = command.callbackClass
        if (StringUtils.isNotBlank(command.callBackHost)) commandRequest.callBackHost = command.callBackHost
        if (StringUtils.isNotBlank(command.callbackKey)) commandRequest.callbackKey = command.callbackKey
        if (StringUtils.isNotBlank(command.methodName)) commandRequest.methodName = command.methodName
        if (StringUtils.isNotBlank(command.rqUid)) commandRequest.rqUid = command.rqUid
        if (StringUtils.isNotBlank(command.serviceClass)) commandRequest.serviceClass = command.serviceClass
        if (StringUtils.isNotBlank(command.sourceModuleId)) commandRequest.sourceModuleId = command.sourceModuleId
        if (command.ticket != null) {
            commandRequest.setUser(command.ticket?.user).token = command.ticket?.token
        }
        for (i in command.methodArgs.indices) {
            commandRequest = commandRequest.addMethodArgs(command.methodArgs[i])
            if (command.args[i] != null) commandRequest.addArgs(ByteString.copyFrom(Serializer.current!!.serialize(command.args[i]))) else commandRequest.addArgs(ByteString.EMPTY)
        }
        return commandRequest.build()
    }

    fun fromGRPCCallbackRequest(callbackRequest: CallbackRequest): CallbackContainer {
        val callbackContainer = CallbackContainer()
        callbackContainer.key = callbackRequest.key
        callbackContainer.listener = callbackRequest.listener
        callbackContainer.resultClass = callbackRequest.resultClass
        if (callbackRequest.result != null && !callbackRequest.result.isEmpty) {
            callbackContainer.result = Serializer.current?.deserializeWithClass(callbackRequest.result.toByteArray())
        }
        return callbackContainer
    }

    fun toGRPCCallbackRequest(callbackContainer: CallbackContainer?): CallbackRequest {
        val callbackRequest = CallbackRequest.newBuilder()
        callbackRequest.key = callbackContainer?.key
        callbackRequest.listener = callbackContainer?.listener
        callbackRequest.resultClass = callbackContainer?.resultClass
        callbackRequest.result = ByteString.copyFrom(Serializer.current?.serializeWithClass(callbackContainer?.result))
        return callbackRequest.build()
    }

    fun toGRPCCommandResponse(response: Any?): CommandResponse {
        val responseMarshalled = ByteString.copyFrom(Serializer.current?.serializeWithClass(response))
        return CommandResponse.newBuilder().setResponse(responseMarshalled).build()
    }

    fun fromGRPCCommandResponse(commandResponse: CommandResponse): Any? {
        return Serializer.current?.deserializeWithClass(commandResponse.response.toByteArray())
    }
}