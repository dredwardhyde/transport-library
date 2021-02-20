package com.jaffa.rpc.test.serialization;

import com.jaffa.rpc.test.servers.AbstractLeaderTestServer;

public class JavaSerializationTest extends AbstractLeaderTestServer {

    static {
        System.setProperty("jaffa.rpc.test.server.protocol", "http");
        System.setProperty("jaffa.rpc.test.server.serializer", "java");
    }

    @Override
    public void stage2() {
        // No-op
    }
}
