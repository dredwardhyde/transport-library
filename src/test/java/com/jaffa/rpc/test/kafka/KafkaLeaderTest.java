package com.jaffa.rpc.test.kafka;

import com.jaffa.rpc.test.servers.AbstractLeaderTestServer;
import kafka.server.KafkaConfig;
import kafka.server.KafkaServer;
import kafka.utils.TestUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.kafka.common.utils.Time;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

@Slf4j
public class KafkaLeaderTest extends AbstractLeaderTestServer {

    private static Path tmpLogDir;
    private static KafkaServer kafkaServer;
    private static final String BROKER_IP_PORT = "127.0.0.1:9092";

    static {
        System.setProperty("jaffa.rpc.protocol", "kafka");
        System.setProperty("jaffa.rpc.protocol.kafka.bootstrap.servers", BROKER_IP_PORT);
    }

    public KafkaLeaderTest() {
        super();
        setFollower(KafkaFollowerTest.class);
    }

    @BeforeAll
    static void setUp() throws Exception {
        tmpLogDir = Files.createTempDirectory("kafka-log-dir-").toAbsolutePath();
        Properties brokerProps = new Properties();
        brokerProps.setProperty("zookeeper.connect", "127.0.0.1:2181");
        brokerProps.setProperty("broker.id", "0");
        brokerProps.setProperty("log.dir", tmpLogDir.toString());
        brokerProps.setProperty("listeners", "PLAINTEXT://" + BROKER_IP_PORT);
        brokerProps.setProperty("offsets.topic.replication.factor", "1");
        brokerProps.setProperty("transaction.state.log.replication.factor", "1");
        brokerProps.setProperty("transaction.state.log.min.isr", "1");
        KafkaConfig config = new KafkaConfig(brokerProps);
        kafkaServer = TestUtils.createServer(config, Time.SYSTEM);
        kafkaServer.startup();
    }

    @AfterAll
    public static void tearDown() {
        if (kafkaServer != null) {
            kafkaServer.shutdown();
            kafkaServer.awaitShutdown();
        }
        try {
            FileUtils.deleteDirectory(new File(tmpLogDir.toString()));
        } catch (IOException e) {
            log.warn("Did not clean " + tmpLogDir.toString(), e);
        }
    }
}
