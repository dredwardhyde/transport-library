package com.jaffa.rpc.lib.serialization;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Serializer {

    public static SerializationContext ctx;

    public static final String CURRENT_SERIALIZATION_PROTOCOL =  System.getProperty("jaffa.rpc.serializer", "kryo");

    public static void init() {
        switch (CURRENT_SERIALIZATION_PROTOCOL) {
            case "kryo":
                ctx = new KryoPoolSerializer();
                break;
            case "java":
                ctx = new JavaSerializer();
                break;
            default:
                log.error("No known serializer defined");
                ctx = null;
                break;
        }
    }
}
