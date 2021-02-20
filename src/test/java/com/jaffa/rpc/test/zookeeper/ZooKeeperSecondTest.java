package com.jaffa.rpc.test.zookeeper;

import com.jaffa.rpc.lib.common.OptionConstants;
import com.jaffa.rpc.lib.entities.Protocol;
import com.jaffa.rpc.lib.exception.JaffaRpcNoRouteException;
import com.jaffa.rpc.lib.exception.JaffaRpcSystemException;
import com.jaffa.rpc.lib.zookeeper.Utils;
import com.jaffa.rpc.test.ZooKeeperExtension;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
@SuppressWarnings({"squid:S5786"})
@ExtendWith({ZooKeeperExtension.class})
public class ZooKeeperSecondTest {
    static {
        System.setProperty("jaffa.rpc.test.server.protocol", "http");
    }

    @Test
    public void stage1() {
        OptionConstants.setModuleId("test.server");
        Utils.connect("localhost:2181");
        Utils.registerService("xxx", Protocol.HTTP);
        System.setProperty("jaffa.rpc.test.server.protocol.http.service.port", "5843");
        Utils.registerService("xxx", Protocol.HTTP);
        try {
            Utils.getHostForService("xxx", "xxx", Protocol.HTTP);
            fail();
        } catch (JaffaRpcNoRouteException e) {
            //No-op
        }
        try {
            Utils.getHostForService("xxx", "test.server", Protocol.HTTP);
        } catch (JaffaRpcNoRouteException e) {
            fail();
        }
        try {
            Utils.getModuleForService("xxx", Protocol.HTTP);
        } catch (JaffaRpcNoRouteException e) {
            fail();
        }
        try {
            Utils.getModuleForService("xxx", Protocol.GRPC);
            fail();
        } catch (JaffaRpcNoRouteException e) {
            //No-op
        }
        try {
            Utils.delete("/xxx", Protocol.HTTP);
        } catch (Exception e) {
            fail();
        }
        try {
            Utils.deleteAllRegistrations("/xxx");
            System.setProperty("jaffa.rpc.test.server.protocol.http.service.port", "4242");
            Utils.deleteAllRegistrations("/xxx");
        } catch (Exception e) {
            fail();
        }
        Utils.cache.refresh("/xxx");
        try {
            Utils.getModuleForService("xxx", Protocol.HTTP);
            fail();
        } catch (JaffaRpcNoRouteException e) {
            //No-op
        }
        try {
            Utils.getHostForService("xxx", "test.server", Protocol.HTTP);
            fail();
        } catch (JaffaRpcNoRouteException e) {
            //No-op
        }
        System.setProperty("jaffa.rpc.test.server.protocol", "xxx");
        try {
            Utils.getCurrentSenderClass();
            fail();
        } catch (JaffaRpcSystemException e) {
            //No-op
        }
        if (Objects.nonNull(Utils.getConn())) {
            try {
                Utils.getConn().close();
            } catch (Exception exception) {
                //No-op
            }
        }
    }
}
