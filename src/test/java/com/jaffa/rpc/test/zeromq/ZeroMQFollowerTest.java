package com.jaffa.rpc.test.zeromq;

import com.jaffa.rpc.test.servers.AbstractFollowerTestServer;

public class ZeroMQFollowerTest extends AbstractFollowerTestServer {

    static {
        System.setProperty("jaffa.rpc.protocol", "zmq");
        System.setProperty("jaffa.rpc.zookeeper.connection", "localhost:2181");
        System.setProperty("jaffa.rpc.protocol.zmq.service.port", "6843");
        System.setProperty("jaffa.rpc.protocol.zmq.callback.port", "6943");
    }

    public static void main(String... args) {
        ZeroMQFollowerTest zeroMQFollowerTest = new ZeroMQFollowerTest();
        zeroMQFollowerTest.testAll();
    }
}
