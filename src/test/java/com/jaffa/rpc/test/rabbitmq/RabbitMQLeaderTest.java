package com.jaffa.rpc.test.rabbitmq;

import com.jaffa.rpc.test.servers.AbstractLeaderTestServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.test.context.ActiveProfiles;

@Slf4j
@ActiveProfiles(profiles = "rabbit-test")
public class RabbitMQLeaderTest extends AbstractLeaderTestServer {

    static {
        System.setProperty("jaffa.rpc.test.server.protocol", "rabbit");
        System.setProperty("jaffa.rpc.test.server.protocol.rabbit.host", "localhost");
        System.setProperty("jaffa.rpc.test.server.protocol.rabbit.port", "5672");
        System.setProperty("jaffa.rpc.test.server.protocol.rabbit.use.ssl", "true");
        System.setProperty("jaffa.rpc.test.server.protocol.rabbit.ssl.truststore.location", "src/test/resources/truststore.jks");
        System.setProperty("jaffa.rpc.test.server.protocol.rabbit.ssl.truststore.password", "simulator");
        System.setProperty("jaffa.rpc.test.server.protocol.rabbit.ssl.keystore.location", "src/test/resources/keystore.jks");
        System.setProperty("jaffa.rpc.test.server.protocol.rabbit.ssl.keystore.password", "simulator");
    }

    @Override
    public void stage2() {
        // No-op
    }
}