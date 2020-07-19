package com.jaffa.rpc.test.http;

import com.jaffa.rpc.test.servers.AbstractFollowerTestServer;

public class HttpFollowerTest extends AbstractFollowerTestServer {

    static {
        System.setProperty("jaffa.rpc.protocol", "http");
        System.setProperty("jaffa.rpc.protocol.http.service.port", "5843");
        System.setProperty("jaffa.rpc.protocol.http.callback.port", "5943");
        System.setProperty("jaffa.rpc.protocol.use.https", "true");
        System.setProperty("jaffa.rpc.protocol.http.ssl.server.truststore.location", "src/test/resources/truststore.jks");
        System.setProperty("jaffa.rpc.protocol.http.ssl.server.truststore.password", "simulator");
        System.setProperty("jaffa.rpc.protocol.http.ssl.server.keystore.location", "src/test/resources/identity.jks");
        System.setProperty("jaffa.rpc.protocol.http.ssl.server.keystore.password", "simulator");
        System.setProperty("jaffa.rpc.protocol.http.ssl.client.truststore.location", "src/test/resources/truststore.jks");
        System.setProperty("jaffa.rpc.protocol.http.ssl.client.truststore.password", "simulator");
        System.setProperty("jaffa.rpc.protocol.http.ssl.client.keystore.location", "src/test/resources/identity.jks");
        System.setProperty("jaffa.rpc.protocol.http.ssl.client.keystore.password", "simulator");
    }

    public static void main(String... args) {
        HttpFollowerTest httpFollowerTest = new HttpFollowerTest();
        httpFollowerTest.testAll();
    }
}
