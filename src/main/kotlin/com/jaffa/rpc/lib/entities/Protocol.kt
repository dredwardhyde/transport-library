package com.jaffa.rpc.lib.entities

import java.io.Serializable
import java.util.*

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