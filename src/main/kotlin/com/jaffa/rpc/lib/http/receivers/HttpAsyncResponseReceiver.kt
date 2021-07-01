package com.jaffa.rpc.lib.http.receivers

import com.google.common.io.ByteStreams
import com.jaffa.rpc.lib.common.OptionConstants
import com.jaffa.rpc.lib.common.RequestInvocationHelper
import com.jaffa.rpc.lib.entities.CallbackContainer
import com.jaffa.rpc.lib.exception.JaffaRpcSystemException
import com.jaffa.rpc.lib.serialization.Serializer
import com.jaffa.rpc.lib.zookeeper.Utils
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import com.sun.net.httpserver.HttpsServer
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.util.concurrent.Executors

class HttpAsyncResponseReceiver : Runnable, Closeable {

    private lateinit var server: HttpServer

    private val log = LoggerFactory.getLogger(HttpAsyncAndSyncRequestReceiver::class.java)

    override fun run() {
        try {
            server = if (System.getProperty(OptionConstants.USE_HTTPS, false.toString()).toBoolean()) {
                val httpsServer = HttpsServer.create(Utils.httpCallbackBindAddress, 0)
                HttpAsyncAndSyncRequestReceiver.initSSLForHttpsServer(
                        httpsServer,
                        Utils.getRequiredOption(OptionConstants.HTTP_SSL_SERVER_TRUSTSTORE_LOCATION),
                        Utils.getRequiredOption(OptionConstants.HTTP_SSL_SERVER_KEYSTORE_LOCATION),
                        Utils.getRequiredOption(OptionConstants.HTTP_SSL_SERVER_TRUSTSTORE_PASSWORD),
                        Utils.getRequiredOption(OptionConstants.HTTP_SSL_SERVER_KEYSTORE_PASSWORD)
                )
                httpsServer
            } else {
                HttpServer.create(Utils.httpCallbackBindAddress, 0)
            }
                    .also { it.createContext("/response", HttpRequestHandler()) }
                    .also { it.executor = Executors.newFixedThreadPool(3) }
                    .also { it.start() }
        } catch (httpServerStartupException: Exception) {
            log.error("Error during HTTP request receiver startup:", httpServerStartupException)
            throw JaffaRpcSystemException(httpServerStartupException)
        }
        log.info("{} started", this.javaClass.simpleName)
    }

    override fun close() {
        server.stop(2)
        log.info("HTTP async response receiver stopped")
    }

    class HttpRequestHandler : HttpHandler {

        private val log = LoggerFactory.getLogger(HttpRequestHandler::class.java)

        override fun handle(request: HttpExchange) {
            try {
                Serializer.current.deserialize(ByteStreams.toByteArray(request.requestBody), CallbackContainer::class.java
                )?.let { RequestInvocationHelper.processCallbackContainer(it) }
                val response = "OK"
                request.sendResponseHeaders(200, response.toByteArray().size.toLong())
                request.responseBody.also { it?.write(response.toByteArray()) }.also { it?.close() }
                request.close()
            } catch (callbackExecutionException: Exception) {
                log.error("HTTP callback execution exception", callbackExecutionException)
            }
        }
    }
}