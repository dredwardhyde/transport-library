package com.jaffa.rpc.test.http;

import com.jaffa.rpc.test.servers.AbstractLeaderTestServer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpLeaderTest extends AbstractLeaderTestServer {

    static {
        System.setProperty("jaffa.rpc.protocol", "http");
    }

    public HttpLeaderTest() {
        super();
        setFollower(HttpFollowerTest.class);
    }
}
