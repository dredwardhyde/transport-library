package com.jaffa.rpc.test.grpc;

import com.jaffa.rpc.test.servers.AbstractLeaderTestServer;

public class gRPCLeaderTestServer extends AbstractLeaderTestServer {

    static {
        System.setProperty("jaffa.rpc.protocol", "grpc");

    }

    public gRPCLeaderTestServer() {
        super();
        setFollower(gRPCFollowerTestServer.class);
    }
}