package com.jaffa.rpc.test.zeromq;

import com.jaffa.rpc.test.servers.AbstractLeaderTestServer;

public class ZeroMQThirdTest extends AbstractLeaderTestServer {

    static {
        System.setProperty("jaffa.rpc.protocol", "zmq");
    }

    @Override
    public void stage2() {
        // No-op
    }
}
