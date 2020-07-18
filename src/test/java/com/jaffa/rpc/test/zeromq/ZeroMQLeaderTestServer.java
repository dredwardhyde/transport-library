package com.jaffa.rpc.test.zeromq;

import com.jaffa.rpc.test.servers.AbstractLeaderTestServer;

public class ZeroMQLeaderTestServer extends AbstractLeaderTestServer {

    static {
        System.setProperty("jaffa.rpc.protocol", "zmq");
        System.setProperty("jaffa.rpc.zookeeper.connection", "localhost:2181");
    }

    public ZeroMQLeaderTestServer() {
        super();
        setFollower(ZeroMQFollowerTestServer.class);
    }
}