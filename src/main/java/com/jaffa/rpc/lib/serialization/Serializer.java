package com.jaffa.rpc.lib.serialization;

import com.jaffa.rpc.lib.common.Options;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Serializer {

    @Getter
    private static boolean isKryo = true;

    @Getter
    private static ObjectSerializer current;

    public static void init() {
        isKryo = System.getProperty(Options.SERIALIZER, "kryo").equals("kryo");
        if (isKryo) {
            current = new KryoPoolSerializer();
        } else {
            current = new JavaSerializer();
        }
    }
}
