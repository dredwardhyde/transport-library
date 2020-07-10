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


@Slf4j
@SuppressWarnings("squid:S1168")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
class KryoPoolSerializer {
    private static final KryoPool pool = new KryoPool.Builder(Kryo::new).softReferences().build();

    public static byte[] serialize(Object obj) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Output output = new Output(baos);
            Kryo kryo = pool.borrow();
            kryo.writeObject(output, obj);
            output.flush();
            output.close();
            pool.release(kryo);
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Error during Kryo object serialization", e);
        }
        return null;
    }

    public static byte[] serializeWithClass(Object obj) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Output output = new Output(baos);
            Kryo kryo = pool.borrow();
            kryo.writeClassAndObject(output, obj);
            output.flush();
            output.close();
            pool.release(kryo);
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("Error during Kryo object with class serialization", e);
        }
        return null;
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
