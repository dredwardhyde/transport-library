package com.jaffa.rpc.test.kafka;

import com.jaffa.rpc.test.servers.AbstractFollowerTestServer;

public class KafkaFollowerTestServer extends AbstractFollowerTestServer {

    static {
        System.setProperty("jaffa.rpc.protocol", "kafka");
        System.setProperty("jaffa.rpc.protocol.kafka.bootstrap.servers", "127.0.0.1:9092");
    }

    public static void main(String... args) {
        KafkaFollowerTestServer kafkaFollowerTestServer = new KafkaFollowerTestServer();
        kafkaFollowerTestServer.testAll();
    }
}