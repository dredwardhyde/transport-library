package com.jaffa.rpc.test;

import lombok.extern.slf4j.Slf4j;
import org.zeromq.SocketType;
import org.zeromq.ZAuth;
import org.zeromq.ZCert;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Slf4j
@SuppressWarnings("squid:S2187")
public class ZMQCertTest {

    private static final String CERTIFICATE_SECRET = "./curve_secret/testcert.pub";
    private static final String CERTIFICATE_PUBLIC = "./curve_public/testcert.pub";
    private static final String CERTIFICATE_FOLDER_PUBLIC = "./curve_public";
    private static final String CERTIFICATE_FOLDER_PRIVATE = "./curve_secret";

    public static void main(String[] args) throws IOException {

        boolean secure = true;

        ZCert client_cert = new ZCert();
        client_cert.setMeta("name", "Client test certificate");
        client_cert.saveSecret(CERTIFICATE_FOLDER_PRIVATE + "/testcert.pub");
        client_cert.savePublic(CERTIFICATE_FOLDER_PUBLIC + "/testcert.pub");

        String localKeys = new String(Files.readAllBytes(Paths.get(CERTIFICATE_SECRET)));
        String serverSecretKey = localKeys.substring(localKeys.indexOf("secret-key = \"") + 14, localKeys.indexOf("secret-key = \"") + 54);
        String serverPublicKey = localKeys.substring(localKeys.indexOf("public-key = \"") + 14, localKeys.indexOf("public-key = \"") + 54);
        log.info("LOCAL SERVER SECRET-KEY = {}", serverSecretKey);
        log.info("LOCAL SERVER PUBLIC-KEY = {}", serverPublicKey);

        String foreignKeys = new String(Files.readAllBytes(Paths.get(CERTIFICATE_PUBLIC)));
        String foreignServerPublicKey = foreignKeys.substring(foreignKeys.indexOf("public-key = \"") + 14, foreignKeys.indexOf("public-key = \"") + 54);
        log.info("FOREIGN SERVER PUBLIC-KEY = {}", foreignServerPublicKey);

        ZContext ctx = new ZContext();
        ctx.setLinger(0);
        ZAuth auth = null;
        if (secure) {
            auth = new ZAuth(ctx);
            auth.allow("127.0.0.1");
            auth.configureCurve(CERTIFICATE_FOLDER_PUBLIC);
        }

        ZMQ.Socket server = ctx.createSocket(SocketType.PUSH);
        if (secure) {
            server.setZAPDomain("global".getBytes());
            server.setCurveServer(true);
            server.setCurvePublicKey(serverPublicKey.getBytes());
            server.setCurveSecretKey(serverSecretKey.getBytes());
        }
        server.bind("tcp://*:9000");

        ZMQ.Socket client = ctx.createSocket(SocketType.PULL);
        if (secure) {
            client.setCurvePublicKey(serverPublicKey.getBytes());
            client.setCurveSecretKey(serverSecretKey.getBytes());
            client.setCurveServerKey(foreignServerPublicKey.getBytes());
        }
        client.connect("tcp://127.0.0.1:9000");

        server.send("Hello");
        String message = client.recvStr(0);

        if (message.equals("Hello")) {
            log.info("Security test OK");
        }
        if (secure) {
            auth.destroy();
        } else {
            ctx.destroy();
        }
    }
}
