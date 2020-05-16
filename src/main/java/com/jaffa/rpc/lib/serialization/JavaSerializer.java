package com.jaffa.rpc.lib.serialization;

import lombok.extern.slf4j.Slf4j;

import java.io.*;

@Slf4j
@SuppressWarnings("squid:S1168")
public class JavaSerializer implements SerializationContext {
    @Override
    public byte[] serialize(Object obj) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            ObjectOutputStream out;
            out = new ObjectOutputStream(bos);
            out.writeObject(obj);
            out.flush();
            return bos.toByteArray();
        } catch (IOException ioException) {
            log.error("Exception while object Java serialization", ioException);
        }
        return null;
    }

    @Override
    public byte[] serializeWithClass(Object obj) {
        return serialize(obj);
    }

    @Override
    public Object deserializeWithClass(byte[] serialized) {
        ByteArrayInputStream bis = new ByteArrayInputStream(serialized);
        try (ObjectInput in = new ObjectInputStream(bis)) {
            return in.readObject();
        } catch (IOException | ClassNotFoundException exception) {
            log.error("Exception while object Java deserialization", exception);
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] serialized, Class<T> clazz) {
        ByteArrayInputStream bis = new ByteArrayInputStream(serialized);
        try (ObjectInput in = new ObjectInputStream(bis)) {
            return (T) in.readObject();
        } catch (IOException | ClassNotFoundException | ClassCastException exception) {
            log.error("Exception while object Java deserialization", exception);
        }
        return null;
    }
}
