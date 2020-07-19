package com.jaffa.rpc.lib.ui;

import com.google.common.io.ByteStreams;
import com.jaffa.rpc.lib.common.Options;
import com.jaffa.rpc.lib.entities.Command;
import com.jaffa.rpc.lib.http.receivers.HttpAsyncAndSyncRequestReceiver;
import com.jaffa.rpc.lib.zookeeper.Utils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsServer;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.QueueUtils;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.Executors;

@Slf4j
@Component
@DependsOn("jaffaService")
@SuppressWarnings("squid:S1191")
public class AdminServer {

    private static final Queue<ResponseMetric> responses = QueueUtils.synchronizedQueue(new CircularFifoQueue<>(1000));
    private HttpServer server;

    public static void addMetric(Command command) {
        double executionDuration = (System.nanoTime() - command.getLocalRequestTime()) / 1000000.0;
        log.trace(">>>>>> Executed request {} in {} ms", command.getRqUid(), executionDuration);
        responses.add(new ResponseMetric(command.getRequestTime(), executionDuration));
    }

    private void respondWithFile(HttpExchange exchange, String fileName) throws IOException {
        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        InputStream is = classloader.getResourceAsStream(fileName);
        if (Objects.isNull(is)) throw new IOException("No such file in resources: " + fileName);
        byte[] page = ByteStreams.toByteArray(is);
        exchange.sendResponseHeaders(200, page.length);
        OutputStream os = exchange.getResponseBody();
        os.write(page);
        os.close();
        exchange.close();
    }

    private void respondWithString(HttpExchange exchange, String response) throws IOException {
        exchange.sendResponseHeaders(200, response.getBytes().length);
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes());
        os.close();
        exchange.close();
    }

    private Integer getFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @PostConstruct
    public void init() {
        try {
            boolean useHttps = Boolean.parseBoolean(System.getProperty(Options.ADMIN_USE_HTTPS, String.valueOf(false)));
            if (useHttps) {
                HttpsServer httpsServer = HttpsServer.create(new InetSocketAddress(Utils.getLocalHost(), getFreePort()), 0);
                HttpAsyncAndSyncRequestReceiver.initSSLForHttpsServer(httpsServer,
                        Utils.getRequiredOption(Options.ADMIN_SSL_TRUSTSTORE_LOCATION),
                        Utils.getRequiredOption(Options.ADMIN_SSL_KEYSTORE_LOCATION),
                        Utils.getRequiredOption(Options.ADMIN_SSL_TRUSTSTORE_PASSWORD),
                        Utils.getRequiredOption(Options.ADMIN_SSL_KEYSTORE_PASSWORD));
                server = httpsServer;
            } else {
                server = HttpServer.create(new InetSocketAddress(Utils.getLocalHost(), getFreePort()), 0);
            }
            server.createContext("/", (HttpExchange exchange) -> {
                String path = exchange.getRequestURI().getPath();
                if ("/admin".equals(path)) {
                    respondWithFile(exchange, "admin.html");
                } else if ("/vis.min.css".equals(path)) {
                    respondWithFile(exchange, "vis.min.css");
                } else if ("/vis.min.js".equals(path)) {
                    respondWithFile(exchange, "vis.min.js");
                } else if ("/protocol".equals(path)) {
                    respondWithString(exchange, Utils.getRpcProtocol().getFullName());
                } else if ("/response".equals(path)) {
                    int count = 0;
                    StringBuilder builder = new StringBuilder();
                    ResponseMetric metric;
                    do {
                        metric = responses.poll();
                        if (Objects.nonNull(metric)) {
                            count++;
                            builder.append(metric.getTime()).append(':').append(metric.getDuration()).append(';');
                        }
                    } while (Objects.nonNull(metric) && count < 30);
                    respondWithString(exchange, builder.toString());
                } else {
                    respondWithString(exchange, "OK");
                }
            });
            server.setExecutor(Executors.newFixedThreadPool(3));
            server.start();
            log.info("Jaffa RPC console started at {}", (useHttps ? "https://" : "http://") + server.getAddress().getHostName() + ":" + server.getAddress().getPort() + "/admin");
        } catch (IOException | KeyStoreException | NoSuchAlgorithmException | CertificateException | UnrecoverableKeyException | KeyManagementException httpServerStartupException) {
            log.error("Exception during admin HTTP server startup", httpServerStartupException);
        }
    }

    @PreDestroy
    public void destroy() {
        if (Objects.nonNull(server)) {
            server.stop(2);
        }
    }

    @Getter
    @AllArgsConstructor
    public static class ResponseMetric {
        private final long time;
        private final double duration;
    }
}
