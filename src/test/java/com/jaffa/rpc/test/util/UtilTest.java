package com.jaffa.rpc.test.util;

import com.jaffa.rpc.lib.JaffaService;
import com.jaffa.rpc.lib.entities.Command;
import com.jaffa.rpc.lib.entities.Protocol;
import com.jaffa.rpc.lib.exception.JaffaRpcExecutionException;
import com.jaffa.rpc.lib.kafka.KafkaRequestSender;
import com.jaffa.rpc.lib.zookeeper.Utils;
import com.jaffa.rpc.lib.zookeeper.ZooKeeperConnection;
import com.jaffa.rpc.test.ZooKeeperExtension;
import kafka.admin.RackAwareMode;
import kafka.server.KafkaConfig;
import kafka.server.KafkaServer;
import kafka.utils.TestUtils;
import kafka.zk.AdminZkClient;
import kafka.zk.KafkaZkClient;
import kafka.zookeeper.ZooKeeperClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.utils.Time;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import scala.Option;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

@Slf4j
@SuppressWarnings({"squid:S5786"})
@ExtendWith({ZooKeeperExtension.class})
public class UtilTest {

    private static final String BROKER_IP_PORT = "127.0.0.1:9092";

    static {
        System.setProperty("jaffa.rpc.module.id", "test.server");
        System.setProperty("jaffa.rpc.protocol", "kafka");
        System.setProperty("jaffa.rpc.protocol.kafka.bootstrap.servers", BROKER_IP_PORT);
        System.setProperty("jaffa.rpc.protocol.kafka.use.ssl", "true");
        System.setProperty("jaffa.rpc.protocol.kafka.ssl.truststore.location", "src/test/resources/truststore.jks");
        System.setProperty("jaffa.rpc.protocol.kafka.ssl.truststore.password", "simulator");
        System.setProperty("jaffa.rpc.protocol.kafka.ssl.keystore.location", "src/test/resources/keystore.jks");
        System.setProperty("jaffa.rpc.protocol.kafka.ssl.keystore.password", "simulator");
        System.setProperty("jaffa.rpc.protocol.kafka.ssl.key.password", "simulator");
    }

    @Test
    public void stage1() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, IOException {
        Utils.connect("localhost:2181");
        JaffaService.getProducerProps().put("max.block.ms", "500");
        ZooKeeperClient zooKeeperClient = new ZooKeeperClient("localhost:2181",
                200000,
                15000,
                10,
                Time.SYSTEM,
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                null, Option.apply(ZooKeeperConnection.getZkConfig()));
        JaffaService.setZkClient(new KafkaZkClient(zooKeeperClient, false, Time.SYSTEM));
        JaffaService.setAdminZkClient(new AdminZkClient(JaffaService.getZkClient()));
        Method method = JaffaService.class.getDeclaredMethod("loadInternalProperties");
        method.setAccessible(true);
        method.invoke(JaffaService.class);
        Properties consumerProps = JaffaService.getConsumerProps();
        Assertions.assertEquals("src/test/resources/truststore.jks", consumerProps.get("ssl.truststore.location"));
        Assertions.assertEquals("simulator", consumerProps.get("ssl.truststore.password"));
        Assertions.assertEquals("src/test/resources/keystore.jks", consumerProps.get("ssl.keystore.location"));
        Assertions.assertEquals("simulator", consumerProps.get("ssl.keystore.password"));
        Assertions.assertEquals("simulator", consumerProps.get("ssl.key.password"));
        KafkaRequestSender.initSyncKafkaConsumers(1, new CountDownLatch(1));
        Path tmpLogDir = Files.createTempDirectory("kafka-log-dir-").toAbsolutePath();
        Properties brokerProps = new Properties();
        brokerProps.setProperty("zookeeper.connect", "127.0.0.1:2181");
        brokerProps.setProperty("broker.id", "0");
        brokerProps.setProperty("log.dir", tmpLogDir.toString());
        brokerProps.setProperty("listeners", "PLAINTEXT://" + BROKER_IP_PORT);
        brokerProps.setProperty("offsets.topic.replication.factor", "1");
        brokerProps.setProperty("transaction.state.log.replication.factor", "1");
        brokerProps.setProperty("transaction.state.log.min.isr", "1");
        KafkaConfig config = new KafkaConfig(brokerProps);
        KafkaServer kafkaServer = TestUtils.createServer(config, Time.SYSTEM);
        kafkaServer.startup();
        JaffaService.getAdminZkClient().createTopic("xxx-test.server-server-async", 1, 1, new Properties(), RackAwareMode.Disabled$.MODULE$);
        JaffaService.getAdminZkClient().createTopic("xxx-test.server-server-sync", 1, 1, new Properties(), RackAwareMode.Disabled$.MODULE$);
        kafkaServer.shutdown();
        kafkaServer.awaitShutdown();
        KafkaRequestSender sender = new KafkaRequestSender();
        Command command = new Command();
        command.setServiceClass("xxx");
        sender.setCommand(command);
        Utils.registerService("xxx", Protocol.KAFKA);
        try {
            sender.executeAsync(new byte[]{});
            Assertions.fail();
        } catch (JaffaRpcExecutionException jaffaRpcExecutionException) {
            //No-op
        }
        try {
            sender.executeSync(new byte[]{});
        } catch (JaffaRpcExecutionException jaffaRpcExecutionException) {
            //No-op
        }
    }
}
