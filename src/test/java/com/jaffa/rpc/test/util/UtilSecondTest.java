package com.jaffa.rpc.test.util;

import com.jaffa.rpc.lib.common.OptionConstants;
import com.jaffa.rpc.lib.exception.JaffaRpcSystemException;
import com.jaffa.rpc.lib.request.RequestImpl;
import com.jaffa.rpc.test.callbacks.ServiceCallback;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@SuppressWarnings({"squid:S5786"})
public class UtilSecondTest {

    @Test
    public void stage1() {
        try {
            new RequestImpl<>(null);
            Assertions.fail();
        } catch (JaffaRpcSystemException e) {
            //No-op
        }
        System.setProperty("jaffa.rpc.test.server.protocol", "http");
        OptionConstants.setModuleId("test.server");
        RequestImpl<Void> request = new RequestImpl<>(null);
        try {
            request.executeAsync(null, ServiceCallback.class);
            Assertions.fail();
        } catch (IllegalArgumentException e) {
            //No-op
        }
        try {
            request.executeAsync(UUID.randomUUID().toString(), null);
            Assertions.fail();
        } catch (IllegalArgumentException e) {
            //No-op
        }
        try {
            request.onModule(null);
            Assertions.fail();
        } catch (IllegalArgumentException e) {
            //No-op
        }
        try {
            request.withTimeout(10, null);
            Assertions.fail();
        } catch (IllegalArgumentException e) {
            //No-op
        }
        try {
            request.withTimeout(-10, TimeUnit.SECONDS);
            Assertions.fail();
        } catch (IllegalArgumentException e) {
            //No-op
        }
    }
}
