package com.jaffa.rpc.test.grpc;

import com.jaffa.rpc.test.servers.AbstractLeaderTestServer;

public class GrpcLeaderTest extends AbstractLeaderTestServer {

    static {
        System.setProperty("jaffa.rpc.protocol", "grpc");

    }

    public GrpcLeaderTest() {
        super();
        setFollower(GrpcFollowerTest.class);
    }
}