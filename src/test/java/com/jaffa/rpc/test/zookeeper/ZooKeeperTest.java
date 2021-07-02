package com.jaffa.rpc.test.zookeeper;

import com.jaffa.rpc.lib.common.OptionConstants;
import com.jaffa.rpc.lib.exception.JaffaRpcSystemException;
import com.jaffa.rpc.lib.zookeeper.Utils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@Slf4j
@SuppressWarnings({"squid:S5786"})
public class ZooKeeperTest {

    @Test
    public void stage1() {
        System.setProperty("jaffa-rpc-config", "src/test/resources/jaffa-rpc-test-config.properties");
        Utils.loadExternalProperties("test.server");
        Assertions.assertEquals("localhost", System.getProperty("jaffa.rpc.test.server.protocol.xxx.host"));
        Assertions.assertEquals("5672", System.getProperty("jaffa.rpc.test.server.protocol.xxx.port"));
        Assertions.assertEquals("simulator", System.getProperty("jaffa.rpc.test.server.protocol.xxx.ssl.keystore.password"));
        System.setProperty("jaffa-rpc-config", "1");
        Utils.loadExternalProperties("test.server");
        try {
            Utils.getRequiredOption("1");
        } catch (IllegalArgumentException illegalArgumentException) {
            Assertions.assertTrue(illegalArgumentException.getMessage().contains("was not set"));
        }
        Assertions.assertEquals("1", Utils.getRequiredOption("jaffa-rpc-config"));
        OptionConstants.setModuleId("test.server");
        System.setProperty("jaffa.rpc.test.server.protocol", "grpc");
        System.setProperty("jaffa.rpc.test.server.protocol.grpc.service.port", "xxx");
        System.setProperty("jaffa.rpc.test.server.protocol.grpc.callback.port", "xxx");
        Assertions.assertEquals(4242, Utils.getServicePort());
        Assertions.assertEquals(4342, Utils.getCallbackPort());
        try {
            System.setProperty("jaffa.rpc.test.server.zookeeper.clientCnxnSocket", "xxx");
            Utils.connect("xxx:666");
        } catch (JaffaRpcSystemException e) {
            Assertions.assertEquals("Couldn't instantiate xxx", e.getCause().getMessage());
        }
        System.getProperty(OptionConstants.INSTANCE.getZOOKEEPER_SSL_KEYSTORE_LOCATION());
        System.getProperty(OptionConstants.INSTANCE.getZOOKEEPER_SSL_KEYSTORE_PASSWORD());
        System.getProperty(OptionConstants.INSTANCE.getZOOKEEPER_SSL_TRUSTSTORE_LOCATION());
        System.getProperty(OptionConstants.INSTANCE.getZOOKEEPER_SSL_TRUSTSTORE_PASSWORD());
    }
}
