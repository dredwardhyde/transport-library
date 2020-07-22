package com.jaffa.rpc.test.util;

import com.jaffa.rpc.lib.JaffaService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Properties;

@Slf4j
@SuppressWarnings({"squid:S5786"})
public class UtilTest {

    private static final String BROKER_IP_PORT = "127.0.0.1:9092";

    static {
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
    public void stage1() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method method = JaffaService.class.getDeclaredMethod("loadInternalProperties");
        method.setAccessible(true);
        method.invoke(JaffaService.class);
        Properties consumerProps = JaffaService.getConsumerProps();
        Assertions.assertEquals("src/test/resources/truststore.jks", consumerProps.get("ssl.truststore.location"));
        Assertions.assertEquals("simulator", consumerProps.get("ssl.truststore.password"));
        Assertions.assertEquals("src/test/resources/keystore.jks", consumerProps.get("ssl.keystore.location"));
        Assertions.assertEquals("simulator", consumerProps.get("ssl.keystore.password"));
        Assertions.assertEquals("simulator", consumerProps.get("ssl.key.password"));
    }
}
