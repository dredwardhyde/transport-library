package com.jaffa.rpc.test.serialization;

import com.jaffa.rpc.test.http.HttpFollowerTest;
import com.jaffa.rpc.test.servers.AbstractLeaderTestServer;

public class JavaSerializationTest extends AbstractLeaderTestServer {

    static {
        System.setProperty("jaffa.rpc.protocol", "http");
        System.setProperty("jaffa.rpc.serializer", "java");
    }

    public JavaSerializationTest() {
        super();
        setFollower(HttpFollowerTest.class);
    }

    @Override
    public void stage2() {
        // No-op
    }
}
