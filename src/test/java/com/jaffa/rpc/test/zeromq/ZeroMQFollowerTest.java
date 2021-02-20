package com.jaffa.rpc.test.zeromq;

import com.jaffa.rpc.test.servers.AbstractFollowerTestServer;

public class ZeroMQFollowerTest extends AbstractFollowerTestServer {

    static {
        System.setProperty("jaffa.rpc.main.server.protocol", "zmq");
        System.setProperty("jaffa.rpc.main.server.zookeeper.connection", "localhost:2181");
        System.setProperty("jaffa.rpc.main.server.protocol.zmq.service.port", "6843");
        System.setProperty("jaffa.rpc.main.server.protocol.zmq.callback.port", "6943");
        System.setProperty("jaffa.rpc.main.server.protocol.zmq.curve.enabled", "true");
        System.setProperty("jaffa.rpc.main.server.protocol.zmq.server.keys", "src/test/resources/curve/curve_secret/testcert.pub");
        System.setProperty("jaffa.rpc.main.server.protocol.zmq.client.key.test.server", "src/test/resources/curve/curve_public/testcert.pub");
        System.setProperty("jaffa.rpc.main.server.protocol.zmq.client.key.main.server", "src/test/resources/curve/curve_public/testcert.pub");
        System.setProperty("jaffa.rpc.main.server.protocol.zmq.client.dir", "src/test/resources/curve/curve_public");
    }

    public static void main(String... args) {
        ZeroMQFollowerTest zeroMQFollowerTest = new ZeroMQFollowerTest();
        zeroMQFollowerTest.testAll();
    }
}
