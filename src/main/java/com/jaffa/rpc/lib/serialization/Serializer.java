package com.jaffa.rpc.lib.serialization;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Serializer {

    public static final boolean IS_KRYO = System.getProperty("jaffa.rpc.serializer", "kryo").equals("kryo");

    public static byte[] serialize(Object obj) {
        if (IS_KRYO) {
            return KryoPoolSerializer.serialize(obj);
        } else {
            return JavaSerializer.serialize(obj);
        }
    }

    public static byte[] serializeWithClass(Object obj) {
        if (IS_KRYO) {
            return KryoPoolSerializer.serializeWithClass(obj);
        } else {
            return JavaSerializer.serializeWithClass(obj);
        }
    }

    public static Object deserializeWithClass(byte[] serialized) {
        if (IS_KRYO) {
            return KryoPoolSerializer.deserializeWithClass(serialized);
        } else {
            return JavaSerializer.deserializeWithClass(serialized);
        }
    }

    public static <T> T deserialize(byte[] serialized, Class<T> clazz) {
        if (IS_KRYO) {
            return KryoPoolSerializer.deserialize(serialized, clazz);
        } else {
            return JavaSerializer.deserialize(serialized, clazz);
        }
    }
}
