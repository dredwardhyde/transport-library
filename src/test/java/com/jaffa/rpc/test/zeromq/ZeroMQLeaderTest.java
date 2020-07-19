package com.jaffa.rpc.test.zeromq;

import com.jaffa.rpc.test.servers.AbstractLeaderTestServer;

public class ZeroMQLeaderTest extends AbstractLeaderTestServer {

    static {
        System.setProperty("jaffa.rpc.protocol", "zmq");
        System.setProperty("jaffa.rpc.zookeeper.connection", "localhost:2181");
        System.setProperty("jaffa.rpc.protocol.zmq.service.port", "6643");
        System.setProperty("jaffa.rpc.protocol.zmq.callback.port", "6343");
    }

    public ZeroMQLeaderTest() {
        super();
        setFollower(ZeroMQFollowerTest.class);
    }
}