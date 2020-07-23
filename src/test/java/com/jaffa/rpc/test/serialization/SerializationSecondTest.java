package com.jaffa.rpc.test.serialization;

import com.jaffa.rpc.lib.serialization.JavaSerializer;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@Slf4j
@SuppressWarnings({"squid:S5786"})
public class SerializationSecondTest {
    @Test
    public void stage1() {
        JavaSerializer javaSerializer = new JavaSerializer();
        Assertions.assertNull(javaSerializer.serialize(new Object()));
        Assertions.assertNull(javaSerializer.serializeWithClass(new Object()));
        Assertions.assertNull(javaSerializer.deserializeWithClass(new byte[]{}));
        Assertions.assertNull(javaSerializer.deserialize(new byte[]{}, Object.class));
    }
}
