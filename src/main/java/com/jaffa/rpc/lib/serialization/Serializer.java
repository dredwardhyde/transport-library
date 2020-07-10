package com.jaffa.rpc.lib.serialization;

import com.jaffa.rpc.lib.common.Options;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Serializer {

    public static boolean isKryo = true;

    public static void init() {
        isKryo = System.getProperty(Options.SERIALIZER, "kryo").equals("kryo");
    }

    public static byte[] serialize(Object obj) {
        if (isKryo) {
            return KryoPoolSerializer.serialize(obj);
        } else {
            return JavaSerializer.serialize(obj);
        }
    }

    public static byte[] serializeWithClass(Object obj) {
        if (isKryo) {
            return KryoPoolSerializer.serializeWithClass(obj);
        } else {
            return JavaSerializer.serializeWithClass(obj);
        }
    }

    public static Object deserializeWithClass(byte[] serialized) {
        if (isKryo) {
            return KryoPoolSerializer.deserializeWithClass(serialized);
        } else {
            return JavaSerializer.deserializeWithClass(serialized);
        }
    }

    public static <T> T deserialize(byte[] serialized, Class<T> clazz) {
        if (isKryo) {
            return KryoPoolSerializer.deserialize(serialized, clazz);
        } else {
            return JavaSerializer.deserialize(serialized);
        }
    }
}
