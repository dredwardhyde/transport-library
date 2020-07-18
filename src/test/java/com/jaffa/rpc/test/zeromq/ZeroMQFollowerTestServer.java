package com.jaffa.rpc.test.zeromq;

import com.jaffa.rpc.test.servers.AbstractFollowerTestServer;

public class ZeroMQFollowerTestServer extends AbstractFollowerTestServer {

    static {
        System.setProperty("jaffa.rpc.protocol", "zmq");
        System.setProperty("jaffa.rpc.zookeeper.connection", "localhost:2181");
        System.setProperty("jaffa.rpc.protocol.zmq.service.port", "5843");
        System.setProperty("jaffa.rpc.protocol.zmq.callback.port", "5943");
    }

    public static void main(String... args) {
        ZeroMQFollowerTestServer zeroMQFollowerTestServer = new ZeroMQFollowerTestServer();
        zeroMQFollowerTestServer.testAll();
    }
}
