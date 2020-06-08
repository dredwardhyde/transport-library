package com.jaffa.rpc.lib.serialization;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.pool.KryoPool;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.ByteArrayOutputStream;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class KryoPoolSerializer {
    private static final int DEFAULT_BUFFER = 1024 * 100;
    private static final KryoPool pool = new KryoPool.Builder(Kryo::new).softReferences().build();

    public static byte[] serialize(Object obj) {
        Output output = new Output(new ByteArrayOutputStream(), DEFAULT_BUFFER);
        Kryo kryo = pool.borrow();
        kryo.writeObject(output, obj);
        byte[] serialized = output.toBytes();
        pool.release(kryo);
        return serialized;
    }

    public static byte[] serializeWithClass(Object obj) {
        Output output = new Output(new ByteArrayOutputStream(), DEFAULT_BUFFER);
        Kryo kryo = pool.borrow();
        kryo.writeClassAndObject(output, obj);
        byte[] serialized = output.toBytes();
        pool.release(kryo);
        return serialized;
    }

    public static Object deserializeWithClass(byte[] serialized) {
        Object obj;
        Kryo kryo = pool.borrow();
        Input input = new Input(serialized);
        obj = kryo.readClassAndObject(input);
        pool.release(kryo);
        return obj;
    }

    public static <T> T deserialize(byte[] serialized, Class<T> clazz) {
        T obj;
        Kryo kryo = pool.borrow();
        Input input = new Input(serialized);
        obj = kryo.readObject(input, clazz);
        pool.release(kryo);
        return obj;
    }
}
