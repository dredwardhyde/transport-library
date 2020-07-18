package com.jaffa.rpc.test.http;

import com.jaffa.rpc.test.servers.AbstractFollowerTestServer;

public class HttpFollowerTestServer extends AbstractFollowerTestServer {

    static {
        System.setProperty("jaffa.rpc.protocol", "http");
        System.setProperty("jaffa.rpc.zookeeper.connection", "localhost:2181");
    }

    public static void main(String... args) {
        HttpFollowerTestServer httpFollowerTestServer = new HttpFollowerTestServer();
        httpFollowerTestServer.testAll();
    }
}
