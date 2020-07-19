package com.jaffa.rpc.test.grpc;

import com.jaffa.rpc.test.servers.AbstractLeaderTestServer;

public class gRPCLeaderTest extends AbstractLeaderTestServer {

    static {
        System.setProperty("jaffa.rpc.protocol", "grpc");

    }

    public gRPCLeaderTest() {
        super();
        setFollower(gRPCFollowerTest.class);
    }
}