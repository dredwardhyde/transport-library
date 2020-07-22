package com.jaffa.rpc.test.zookeeper;

import com.jaffa.rpc.lib.entities.Protocol;
import com.jaffa.rpc.lib.exception.JaffaRpcNoRouteException;
import com.jaffa.rpc.lib.exception.JaffaRpcSystemException;
import com.jaffa.rpc.lib.zookeeper.Utils;
import com.jaffa.rpc.test.ZooKeeperExtension;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Objects;

@Slf4j
@SuppressWarnings({"squid:S5786"})
@ExtendWith({ZooKeeperExtension.class})
public class ZooKeeperSecondTest {
    static {
        System.setProperty("jaffa.rpc.module.id", "test.server");
        System.setProperty("jaffa.rpc.protocol", "http");
    }

    @Test
    public void stage1() {
        Utils.connect("localhost:2181");
        Utils.registerService("xxx", Protocol.HTTP);
        System.setProperty("jaffa.rpc.protocol.http.service.port", "5843");
        Utils.registerService("xxx", Protocol.HTTP);
        try {
            Utils.getHostForService("xxx", "xxx", Protocol.HTTP);
            Assertions.fail();
        } catch (JaffaRpcNoRouteException e) {
            log.error("Error occurred", e);
        }
        try {
            Utils.getHostForService("xxx", "test.server", Protocol.HTTP);
        } catch (JaffaRpcNoRouteException e) {
            log.error("Error occurred", e);
            Assertions.fail();
        }
        try {
            Utils.getModuleForService("xxx", Protocol.HTTP);
        } catch (JaffaRpcNoRouteException e) {
            log.error("Error occurred", e);
            Assertions.fail();
        }
        try {
            Utils.getModuleForService("xxx", Protocol.GRPC);
            Assertions.fail();
        } catch (JaffaRpcNoRouteException e) {
            log.error("Error occurred", e);
        }
        try {
            Utils.delete("/xxx", Protocol.HTTP);
        } catch (Exception e) {
            log.error("Error occurred", e);
            Assertions.fail();
        }
        try {
            Utils.deleteAllRegistrations("/xxx");
            System.setProperty("jaffa.rpc.protocol.http.service.port", "4242");
            Utils.deleteAllRegistrations("/xxx");
        } catch (Exception e) {
            log.error("Error occurred", e);
            Assertions.fail();
        }
        Utils.cache.refresh("/xxx");
        try {
            Utils.getModuleForService("xxx", Protocol.HTTP);
            Assertions.fail();
        } catch (JaffaRpcNoRouteException e) {
            log.error("Error occurred", e);

        }
        try {
            Utils.getHostForService("xxx", "test.server", Protocol.HTTP);
            Assertions.fail();
        } catch (JaffaRpcNoRouteException e) {
            log.error("Error occurred", e);
        }
        System.setProperty("jaffa.rpc.protocol", "xxx");
        try {
            Utils.getCurrentSenderClass();
            Assertions.fail();
        } catch (JaffaRpcSystemException e) {
            log.error("Error occurred", e);
        }
        if (Objects.nonNull(Utils.getConn())) {
            try {
                Utils.getConn().close();
            } catch (Exception exception) {
                log.error("Exception occurred during ZooKeeper client shutdown", exception);
            }
        }
    }
}
