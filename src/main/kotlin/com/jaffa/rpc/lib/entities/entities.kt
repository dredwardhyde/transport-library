package com.jaffa.rpc.lib.entities

import com.jaffa.rpc.lib.security.SecurityTicket
import java.io.Serializable
import java.util.*

class CallbackContainer : Serializable {
    var key: String? = null
    var listener: String? = null
    var result: Any? = null
    var resultClass: String? = null

    override fun toString(): String {
        return "CallbackContainer(key=$key, listener=$listener, result=$result, resultClass=$resultClass)"
    }
}

class Command : Serializable {
    var serviceClass: String? = null
    var methodName: String? = null
    var methodArgs: Array<String> = emptyArray()
    var args: Array<Any?> = emptyArray()
    var callbackClass: String? = null
    var callbackKey: String? = null
    var callBackHost: String? = null
    var sourceModuleId: String? = null
    var rqUid: String? = null
    var ticket: SecurityTicket? = null
    var asyncExpireTime: Long = 0
    var requestTime: Long = 0
    var localRequestTime: Long = 0

    override fun toString(): String {
        return "Command(serviceClass=$serviceClass, " +
                "methodName=$methodName, " +
                "methodArgs=${methodArgs.contentToString()}, " +
                "args=${args.contentToString()}, " +
                "callbackClass=$callbackClass, " +
                "callbackKey=$callbackKey, " +
                "callBackHost=$callBackHost, " +
                "sourceModuleId=$sourceModuleId, " +
                "rqUid=$rqUid, ticket=$ticket, " +
                "asyncExpireTime=$asyncExpireTime, " +
                "requestTime=$requestTime, " +
                "localRequestTime=$localRequestTime)"
    }
}

class ExceptionHolder(toString: String?) : Serializable {
    val stackTrace: String? = toString

    constructor() : this(null)
}

enum class Protocol(val shortName: String, val fullName: String) : Serializable {
    KAFKA("kafka", "Apache Kafka"),
    ZMQ("zmq", "ZeroMQ"),
    HTTP("http", "HTTP/1.1"),
    GRPC("grpc", "gRPC"),
    RABBIT("rabbit", "RabbitMQ");

    companion object {
        fun getByName(name: String): Protocol? {
            return Arrays.stream(values()).filter { x: Protocol -> x.shortName == name }.findFirst().orElse(null)
        }
    }
}



