package com.jaffa.rpc.test.http;

import com.jaffa.rpc.test.servers.AbstractLeaderTestServer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpLeaderTest extends AbstractLeaderTestServer {

    static {
        System.setProperty("jaffa.rpc.protocol", "http");
        System.setProperty("jaffa.rpc.protocol.use.https", "true");
        System.setProperty("jaffa.rpc.test.mode", "false");
        System.setProperty("jaffa.rpc.protocol.http.ssl.server.truststore.location", "src/test/resources/truststore.jks");
        System.setProperty("jaffa.rpc.protocol.http.ssl.server.truststore.password", "simulator");
        System.setProperty("jaffa.rpc.protocol.http.ssl.server.keystore.location", "src/test/resources/keystore.jks");
        System.setProperty("jaffa.rpc.protocol.http.ssl.server.keystore.password", "simulator");
        System.setProperty("jaffa.rpc.protocol.http.ssl.client.truststore.location", "src/test/resources/truststore.jks");
        System.setProperty("jaffa.rpc.protocol.http.ssl.client.truststore.password", "simulator");
        System.setProperty("jaffa.rpc.protocol.http.ssl.client.keystore.location", "src/test/resources/keystore.jks");
        System.setProperty("jaffa.rpc.protocol.http.ssl.client.keystore.password", "simulator");
    }

    public HttpLeaderTest() {
        super();
        setFollower(HttpFollowerTest.class);
    }
}
