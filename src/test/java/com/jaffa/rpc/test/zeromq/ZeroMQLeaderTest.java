package com.jaffa.rpc.test.zeromq;

import com.jaffa.rpc.test.servers.AbstractLeaderTestServer;

public class ZeroMQLeaderTest extends AbstractLeaderTestServer {

    static {
        System.setProperty("jaffa.rpc.test.server.protocol", "zmq");
        System.setProperty("jaffa.rpc.test.server.protocol.zmq.service.port", "6643");
        System.setProperty("jaffa.rpc.test.server.protocol.zmq.callback.port", "6343");
        System.setProperty("jaffa.rpc.test.server.protocol.zmq.curve.enabled", "true");
        System.setProperty("jaffa.rpc.test.server.protocol.zmq.server.keys", "src/test/resources/curve/curve_secret/testcert.pub");
        System.setProperty("jaffa.rpc.test.server.protocol.zmq.client.key.test.server", "src/test/resources/curve/curve_public/testcert.pub");
        System.setProperty("jaffa.rpc.test.server.protocol.zmq.client.key.main.server", "src/test/resources/curve/curve_public/testcert.pub");
        System.setProperty("jaffa.rpc.test.server.protocol.zmq.client.dir", "src/test/resources/curve/curve_public");
    }

    public ZeroMQLeaderTest() {
        super();
        setFollower(ZeroMQFollowerTest.class);
    }
}