package com.jaffa.rpc.lib.serialization;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Serializer {

    @Getter
    private static SerializationContext ctx;

    public static String getCurrentSerializationProtocol(){
        return System.getProperty("jaffa.rpc.serializer", "kryo");
    }

    public static void init() {
        String currentSerializer = System.getProperty("jaffa.rpc.serializer", "kryo");
        switch (currentSerializer) {
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
