package com.jaffa.rpc.test.grpc;

import com.jaffa.rpc.test.servers.AbstractFollowerTestServer;

public class GrpcFollowerTest extends AbstractFollowerTestServer {

    static {
        System.setProperty("jaffa.rpc.protocol", "grpc");
        System.setProperty("jaffa.rpc.protocol.grpc.service.port", "5843");
        System.setProperty("jaffa.rpc.protocol.grpc.callback.port", "5943");
        System.setProperty("jaffa.rpc.protocol.grpc.use.ssl", "true");
        System.setProperty("jaffa.rpc.protocol.grpc.ssl.server.key.location", "src/test/resources/server.key");
        System.setProperty("jaffa.rpc.protocol.grpc.ssl.server.store.location", "src/test/resources/server.crt");
        System.setProperty("jaffa.rpc.protocol.grpc.ssl.client.key.location", "src/test/resources/client.key");
        System.setProperty("jaffa.rpc.protocol.grpc.ssl.client.keystore.location", "src/test/resources/client.crt");
        System.setProperty("jaffa.rpc.protocol.grpc.ssl.client.truststore.location", "src/test/resources/server.crt");
    }

    public static void main(String... args) {
        GrpcFollowerTest gRPCFollowerTest = new GrpcFollowerTest();
        gRPCFollowerTest.testAll();
    }
}
