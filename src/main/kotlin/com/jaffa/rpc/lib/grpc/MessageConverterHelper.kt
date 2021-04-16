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
        with(command) {
            callBackHost = request.callBackHost
            callbackClass = request.callbackClass
            asyncExpireTime = request.asyncExpireTime
            methodName = request.methodName
            callbackKey = request.callbackKey
            localRequestTime = request.localRequestTime
            requestTime = request.requestTime
            rqUid = request.rqUid
            sourceModuleId = request.sourceModuleId
            serviceClass = request.serviceClass
        }
        if (StringUtils.isNotBlank(request.token) && StringUtils.isNotBlank(request.user)) {
            val securityTicket = SecurityTicket()
            with(securityTicket) {
                token = request.token
                user = request.user
            }
            command.ticket = securityTicket
        }
        if (request.methodArgsList != null && !request.methodArgsList.isEmpty()) {
            val methodArgs = request.methodArgsList.toTypedArray()
            val argsObjects = arrayOfNulls<Any>(methodArgs.size)
            for (i in methodArgs.indices) {
                if (request.getArgs(i) == ByteString.EMPTY)
                    argsObjects[i] = null
                else
                    argsObjects[i] = Serializer.current.deserialize(request.getArgs(i).toByteArray(), Class.forName(methodArgs[i]))
            }
            command.methodArgs = methodArgs
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
        command.ticket?.let { commandRequest.setUser(it.user).token = it.token }
        with(commandRequest) {
            if (StringUtils.isNotBlank(command.callbackClass)) callbackClass = command.callbackClass
            if (StringUtils.isNotBlank(command.callBackHost)) callBackHost = command.callBackHost
            if (StringUtils.isNotBlank(command.callbackKey)) callbackKey = command.callbackKey
            if (StringUtils.isNotBlank(command.methodName)) methodName = command.methodName
            if (StringUtils.isNotBlank(command.rqUid)) rqUid = command.rqUid
            if (StringUtils.isNotBlank(command.serviceClass)) serviceClass = command.serviceClass
            if (StringUtils.isNotBlank(command.sourceModuleId)) sourceModuleId = command.sourceModuleId
        }
        for (i in command.methodArgs.indices) {
            commandRequest = commandRequest.addMethodArgs(command.methodArgs[i])
            if (command.args[i] != null)
                commandRequest.addArgs(ByteString.copyFrom(Serializer.current.serialize(command.args[i])))
            else
                commandRequest.addArgs(ByteString.EMPTY)
        }
        return commandRequest.build()
    }

    fun fromGRPCCallbackRequest(callbackRequest: CallbackRequest): CallbackContainer {
        val callbackContainer = CallbackContainer()
        with(callbackContainer) {
            key = callbackRequest.key
            listener = callbackRequest.listener
            resultClass = callbackRequest.resultClass
            if (callbackRequest.result != null && !callbackRequest.result.isEmpty) {
                result = Serializer.current.deserializeWithClass(callbackRequest.result.toByteArray())
            }
        }
        return callbackContainer
    }

    fun toGRPCCallbackRequest(callbackContainer: CallbackContainer?): CallbackRequest {
        val callbackRequest = CallbackRequest.newBuilder()
        with(callbackRequest) {
            key = callbackContainer?.key
            listener = callbackContainer?.listener
            resultClass = callbackContainer?.resultClass
            result = ByteString.copyFrom(Serializer.current.serializeWithClass(callbackContainer?.result))
        }
        return callbackRequest.build()
    }

    fun toGRPCCommandResponse(response: Any?): CommandResponse {
        return CommandResponse.newBuilder().setResponse(ByteString.copyFrom(Serializer.current.serializeWithClass(response))).build()
    }

    fun fromGRPCCommandResponse(commandResponse: CommandResponse): Any? {
        return Serializer.current.deserializeWithClass(commandResponse.response.toByteArray())
    }
}