package com.jaffa.rpc.test.rabbitmq;

import com.jaffa.rpc.test.servers.AbstractLeaderTestServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.test.context.ActiveProfiles;

@Slf4j
@ActiveProfiles(profiles = "rabbit-test")
public class RabbitMQLeaderTest extends AbstractLeaderTestServer {

    static {
        System.setProperty("jaffa.rpc.protocol", "rabbit");
        System.setProperty("jaffa.rpc.protocol.rabbit.host", "localhost");
        System.setProperty("jaffa.rpc.protocol.rabbit.port", "5672");
    }

    @Override
    public void stage2() {
        // No-op
    }
}