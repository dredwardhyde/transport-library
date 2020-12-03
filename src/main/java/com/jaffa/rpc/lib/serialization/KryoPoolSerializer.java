package com.jaffa.rpc.lib.serialization;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.Pool;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;


@Slf4j
@SuppressWarnings("squid:S1168")
public final class KryoPoolSerializer implements ObjectSerializer {
    private final Pool<Kryo> pool;

    public KryoPoolSerializer() {
        pool = new Pool<Kryo>(true, true, 100) {
            protected Kryo create() {
                Kryo kryo = new Kryo();
                kryo.register(void.class);
                kryo.setRegistrationRequired(false);
                kryo.setReferences(true);
                return kryo;
            }
        };
    }

    @Override
    public byte[] serialize(Object obj) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Output output = new Output(baos);
        Kryo kryo = pool.obtain();
        kryo.writeObject(output, obj);
        output.flush();
        output.close();
        pool.free(kryo);
        return baos.toByteArray();
    }

    @Override
    public byte[] serializeWithClass(Object obj) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Output output = new Output(baos);
        Kryo kryo = pool.obtain();
        kryo.writeClassAndObject(output, obj);
        output.flush();
        output.close();
        pool.free(kryo);
        return baos.toByteArray();
    }

    @Override
    public Object deserializeWithClass(byte[] serialized) {
        Object obj;
        Kryo kryo = pool.obtain();
        Input input = new Input(serialized);
        obj = kryo.readClassAndObject(input);
        pool.free(kryo);
        return obj;
    }

    @Override
    public <T> T deserialize(byte[] serialized, Class<T> clazz) {
        T obj;
        Kryo kryo = pool.obtain();
        Input input = new Input(serialized);
        obj = kryo.readObject(input, clazz);
        pool.free(kryo);
        return obj;
    }
}
