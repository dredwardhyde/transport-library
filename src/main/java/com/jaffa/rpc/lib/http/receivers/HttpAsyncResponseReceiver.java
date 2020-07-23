package com.jaffa.rpc.lib.http.receivers;

import com.google.common.io.ByteStreams;
import com.jaffa.rpc.lib.common.OptionConstants;
import com.jaffa.rpc.lib.common.RequestInvocationHelper;
import com.jaffa.rpc.lib.entities.CallbackContainer;
import com.jaffa.rpc.lib.exception.JaffaRpcSystemException;
import com.jaffa.rpc.lib.serialization.Serializer;
import com.jaffa.rpc.lib.zookeeper.Utils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsServer;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.io.OutputStream;
import java.util.concurrent.Executors;

@Slf4j
@SuppressWarnings("squid:S1191")
public class HttpAsyncResponseReceiver implements Runnable, Closeable {

    private HttpServer server;

    @Override
    public void run() {
        try {
            if (Boolean.parseBoolean(System.getProperty(OptionConstants.USE_HTTPS, String.valueOf(false)))) {
                HttpsServer httpsServer = HttpsServer.create(Utils.getHttpCallbackBindAddress(), 0);
                HttpAsyncAndSyncRequestReceiver.initSSLForHttpsServer(httpsServer,
                        Utils.getRequiredOption(OptionConstants.HTTP_SSL_SERVER_TRUSTSTORE_LOCATION),
                        Utils.getRequiredOption(OptionConstants.HTTP_SSL_SERVER_KEYSTORE_LOCATION),
                        Utils.getRequiredOption(OptionConstants.HTTP_SSL_SERVER_TRUSTSTORE_PASSWORD),
                        Utils.getRequiredOption(OptionConstants.HTTP_SSL_SERVER_KEYSTORE_PASSWORD));
                server = httpsServer;
            } else {
                server = HttpServer.create(Utils.getHttpCallbackBindAddress(), 0);
            }
            server.createContext("/response", new HttpRequestHandler());
            server.setExecutor(Executors.newFixedThreadPool(3));
            server.start();
        } catch (Exception httpServerStartupException) {
            log.error("Error during HTTP request receiver startup:", httpServerStartupException);
            throw new JaffaRpcSystemException(httpServerStartupException);
        }
        log.info("{} started", this.getClass().getSimpleName());
    }

    @Override
    public void close() {
        server.stop(2);
        log.info("HTTP async response receiver stopped");
    }

    public static class HttpRequestHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange request) {
            try {
                CallbackContainer callbackContainer = Serializer.getCurrent().deserialize(ByteStreams.toByteArray(request.getRequestBody()), CallbackContainer.class);
                RequestInvocationHelper.processCallbackContainer(callbackContainer);
                String response = "OK";
                request.sendResponseHeaders(200, response.getBytes().length);
                OutputStream os = request.getResponseBody();
                os.write(response.getBytes());
                os.close();
                request.close();
            } catch (Exception callbackExecutionException) {
                log.error("HTTP callback execution exception", callbackExecutionException);
            }
        }
    }
}
