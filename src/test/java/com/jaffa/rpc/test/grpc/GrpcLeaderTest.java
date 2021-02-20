package com.jaffa.rpc.test.grpc;

import com.jaffa.rpc.test.servers.AbstractLeaderTestServer;

public class GrpcLeaderTest extends AbstractLeaderTestServer {

    static {
        System.setProperty("jaffa.rpc.test.server.protocol", "grpc");
        System.setProperty("jaffa.rpc.test.server.protocol.grpc.use.ssl", "true");
        System.setProperty("jaffa.rpc.test.server.protocol.grpc.ssl.server.key.location", "src/test/resources/server.key");
        System.setProperty("jaffa.rpc.test.server.protocol.grpc.ssl.server.store.location", "src/test/resources/server.crt");
        System.setProperty("jaffa.rpc.test.server.protocol.grpc.ssl.client.key.location", "src/test/resources/client.key");
        System.setProperty("jaffa.rpc.test.server.protocol.grpc.ssl.client.keystore.location", "src/test/resources/client.crt");
        System.setProperty("jaffa.rpc.test.server.protocol.grpc.ssl.client.truststore.location", "src/test/resources/server.crt");
    }

    public GrpcLeaderTest() {
        super();
        setFollower(GrpcFollowerTest.class);
    }
}