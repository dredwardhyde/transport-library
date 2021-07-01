package com.jaffa.rpc.lib.entities

import com.jaffa.rpc.lib.security.SecurityTicket
import java.io.Serializable
import java.util.*

class CallbackContainer : Serializable {
    var key: String? = null
    var listener: String? = null
    var result: Any? = null
    var resultClass: String? = null
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



