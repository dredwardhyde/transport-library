package com.jaffa.rpc.test.grpc;

import com.jaffa.rpc.test.servers.AbstractFollowerTestServer;

public class GrpcFollowerTest extends AbstractFollowerTestServer {

    static {
        System.setProperty("jaffa.rpc.protocol", "grpc");
        System.setProperty("jaffa.rpc.protocol.grpc.service.port", "5843");
        System.setProperty("jaffa.rpc.protocol.grpc.callback.port", "5943");
    }

    public static void main(String... args) {
        GrpcFollowerTest gRPCFollowerTest = new GrpcFollowerTest();
        gRPCFollowerTest.testAll();
    }
}