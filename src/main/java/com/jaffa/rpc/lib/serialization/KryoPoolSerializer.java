package com.jaffa.rpc.lib.serialization;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.pool.KryoPool;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
class KryoPoolSerializer {
    private static final KryoPool pool = new KryoPool.Builder(Kryo::new).softReferences().build();

    public static byte[] serialize(Object obj) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Output output = new Output(baos);
        Kryo kryo = pool.borrow();
        kryo.writeObject(output, obj);
        output.flush();
        output.close();
        pool.release(kryo);
        byte[] serialized = baos.toByteArray();
        try {
            baos.close();
        } catch (IOException e) {
            log.error("Error during Kryo object serialization", e);
        }
        return serialized;
    }

    public static byte[] serializeWithClass(Object obj) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Output output = new Output(baos);
        Kryo kryo = pool.borrow();
        kryo.writeClassAndObject(output, obj);
        output.flush();
        output.close();
        pool.release(kryo);
        byte[] serialized = baos.toByteArray();
        try {
            baos.close();
        } catch (IOException e) {
            log.error("Error during Kryo object with class serialization", e);
        }
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
