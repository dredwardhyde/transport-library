package com.jaffa.rpc.lib.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;
import java.util.Arrays;

@AllArgsConstructor
@Getter
public enum Protocol implements Serializable {

    KAFKA("kafka", "Apache Kafka"),
    ZMQ("zmq", "ZeroMQ"),
    HTTP("http", "HTTP/1.1"),
    GRPC("grpc", "gRPC"),
    RABBIT("rabbit", "RabbitMQ");

    private final String shortName;
    private final String fullName;

    public static Protocol getByName(String name) {
        return Arrays.stream(Protocol.values()).
                filter(x -> x.getShortName().equals(name)).
                findFirst().
                orElse(null);
    }
}
