package com.jaffa.rpc.lib.zeromq;

import com.jaffa.rpc.lib.common.OptionConstants;
import com.jaffa.rpc.lib.zookeeper.Utils;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.zeromq.ZMQ;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CurveUtils {

    private static final Map<String, String> moduleIdWithClientKeys = new HashMap<>();
    @Getter
    private static String serverPublicKey;
    @Getter
    private static String serverSecretKey;

    private static String getPublicKeyFromPath(@NotNull String path) {
        try {
            String keys = new String(Files.readAllBytes(Paths.get(path)));
            return keys.substring(keys.indexOf("public-key = \"") + 14, keys.indexOf("public-key = \"") + 54);
        } catch (IOException ioException) {
            log.error("Error while getting public Curve key from location " + path, ioException);
        }
        return null;
    }

    public static void makeSocketSecure(@NotNull ZMQ.Socket socket) {
        if (Boolean.parseBoolean(System.getProperty(OptionConstants.ZMQ_CURVE_ENABLED, String.valueOf(false)))) {
            socket.setZAPDomain("global".getBytes());
            socket.setCurveServer(true);
            socket.setCurvePublicKey(CurveUtils.getServerPublicKey().getBytes());
            socket.setCurveSecretKey(CurveUtils.getServerSecretKey().getBytes());
        }
    }

    public static String getClientPublicKey(@NotNull String moduleId) {
        String clientPublicKey = moduleIdWithClientKeys.get(moduleId);
        log.debug("Reading public client key {} for {}", clientPublicKey, moduleId);
        return clientPublicKey;
    }

    private static String getSecretKeyFromPath(@NotNull String path) {
        try {
            String keys = new String(Files.readAllBytes(Paths.get(path)));
            return keys.substring(keys.indexOf("secret-key = \"") + 14, keys.indexOf("secret-key = \"") + 54);
        } catch (IOException ioException) {
            log.error("Error while getting secret Curve key from location " + path, ioException);
        }
        return null;
    }

    public static void readClientKeys() {
        for (Map.Entry<Object, Object> property : System.getProperties().entrySet()) {
            String name = String.valueOf(property.getKey());
            if (!name.startsWith(OptionConstants.ZMQ_CLIENT_KEY)) continue;
            String path = String.valueOf(property.getValue());
            String moduleId = name.replace(OptionConstants.ZMQ_CLIENT_KEY, "");
            moduleIdWithClientKeys.put(moduleId, getPublicKeyFromPath(path));
        }
    }

    public static void readServerKeys() {
        String localServerKeys = Utils.getRequiredOption(OptionConstants.ZMQ_SERVER_KEYS);
        serverPublicKey = getPublicKeyFromPath(localServerKeys);
        serverSecretKey = getSecretKeyFromPath(localServerKeys);
    }
}
