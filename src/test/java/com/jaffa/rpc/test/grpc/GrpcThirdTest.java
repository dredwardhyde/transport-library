package com.jaffa.rpc.test.grpc;

import com.jaffa.rpc.test.servers.AbstractLeaderTestServer;

public class GrpcThirdTest extends AbstractLeaderTestServer {

    static {
        System.setProperty("jaffa.rpc.protocol", "grpc");
    }

    @Override
    public void stage2() {
        // No-op
    }
}
