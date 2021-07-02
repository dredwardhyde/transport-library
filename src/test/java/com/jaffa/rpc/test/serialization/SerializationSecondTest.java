package com.jaffa.rpc.test.serialization;

import com.jaffa.rpc.lib.exception.JaffaRpcExecutionException;
import com.jaffa.rpc.lib.exception.JaffaRpcNoRouteException;
import com.jaffa.rpc.lib.serialization.JavaSerializer;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
@SuppressWarnings({"squid:S5786"})
public class SerializationSecondTest {
    @Test
    public void stage1() {
        JavaSerializer javaSerializer = new JavaSerializer();
        try {
            javaSerializer.serialize(new Object());
            fail();
        } catch (JaffaRpcExecutionException jaffaRpcExecutionException) {
            //No-op
        }

        try {
            javaSerializer.serializeWithClass(new Object());
            fail();
        } catch (JaffaRpcExecutionException jaffaRpcExecutionException) {
            //No-op
        }

        try {
            javaSerializer.deserializeWithClass(new byte[]{});
            fail();
        } catch (JaffaRpcExecutionException jaffaRpcExecutionException) {
            //No-op
        }

        try {
            javaSerializer.deserialize(new byte[]{}, Object.class);
            fail();
        } catch (JaffaRpcExecutionException jaffaRpcExecutionException) {
            //No-op
        }
    }
}
