package com.jaffa.rpc.lib.serialization;

public interface ObjectSerializer {
    byte[] serialize(Object obj);

    byte[] serializeWithClass(Object obj);

    Object deserializeWithClass(byte[] serialized);

    <T> T deserialize(byte[] serialized, Class<T> clazz);
}
