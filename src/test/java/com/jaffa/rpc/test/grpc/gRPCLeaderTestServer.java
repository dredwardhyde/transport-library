package com.jaffa.rpc.test.grpc;

import com.jaffa.rpc.test.servers.AbstractLeaderTestServer;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

public class gRPCLeaderTestServer extends AbstractLeaderTestServer {

    private static TestingServer zkServer;

    static {
        System.setProperty("jaffa.rpc.protocol", "grpc");
        System.setProperty("jaffa.rpc.zookeeper.connection", "localhost:2181");
    }

    public gRPCLeaderTestServer() {
        super();
        setFollower(gRPCFollowerTestServer.class);
    }

    @BeforeAll
    static void setUp() throws Exception {
        zkServer = new TestingServer(2181, true);
    }

    @AfterAll
    public static void tearDown() throws Exception {
        zkServer.close();
    }
}