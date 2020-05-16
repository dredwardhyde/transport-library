package com.jaffa.rpc.lib.zeromq;

import com.jaffa.rpc.lib.exception.JaffaRpcSystemException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private static final String CLIENT_KEY_PREFIX = "jaffa.rpc.protocol.zmq.client.key.";
    private static final String SERVER_PROPERTY_NAME = "jaffa.rpc.protocol.zmq.server.keys";
    @Getter
    private static String serverPublicKey;
    @Getter
    private static String serverSecretKey;

    private static String getPublicKeyFromPath(String path) {
        try {
            String keys = new String(Files.readAllBytes(Paths.get(path)));
            return keys.substring(keys.indexOf("public-key = \"") + 14, keys.indexOf("public-key = \"") + 54);
        } catch (IOException ioException) {
            log.error("Error while getting public Curve key from location " + path, ioException);
        }
        return null;
    }

    public static void makeSocketSecure(ZMQ.Socket socket) {
        if (Boolean.parseBoolean(System.getProperty("jaffa.rpc.protocol.zmq.curve.enabled", String.valueOf(false)))) {
            socket.setZAPDomain("global".getBytes());
            socket.setCurveServer(true);
            socket.setCurvePublicKey(CurveUtils.getServerPublicKey().getBytes());
            socket.setCurveSecretKey(CurveUtils.getServerSecretKey().getBytes());
        }
    }

    public static String getClientPublicKey(String moduleId) {
        String clientPublicKey = moduleIdWithClientKeys.get(moduleId);
        log.info("Reading public client key {} for {}", clientPublicKey, moduleId);
        return clientPublicKey;
    }

    private static String getSecretKeyFromPath(String path) {
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
            if (!name.startsWith(CLIENT_KEY_PREFIX)) continue;
            String path = String.valueOf(property.getValue());
            String moduleId = name.replace(CLIENT_KEY_PREFIX, "");
            moduleIdWithClientKeys.put(moduleId, getPublicKeyFromPath(path));
        }
    }

    public static void readServerKeys() {
        String localServerKeys = System.getProperty(SERVER_PROPERTY_NAME);
        if (localServerKeys == null)
            throw new JaffaRpcSystemException("No local server keys were provided with ZeroMQ Curve enabled!");
        serverPublicKey = getPublicKeyFromPath(localServerKeys);
        serverSecretKey = getSecretKeyFromPath(localServerKeys);
    }
}
