package com.jaffa.rpc.test.grpc;

import com.jaffa.rpc.test.servers.AbstractFollowerTestServer;

public class gRPCFollowerTestServer extends AbstractFollowerTestServer {

    static {
        System.setProperty("jaffa.rpc.protocol", "grpc");
        System.setProperty("jaffa.rpc.zookeeper.connection", "localhost:2181");
    }

    public static void main(String... args) {
        gRPCFollowerTestServer gRPCFollowerTestServer = new gRPCFollowerTestServer();
        gRPCFollowerTestServer.testAll();
    }
}