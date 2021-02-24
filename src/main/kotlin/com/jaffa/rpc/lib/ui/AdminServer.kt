package com.jaffa.rpc.lib.ui

import com.google.common.io.ByteStreams
import com.jaffa.rpc.lib.common.OptionConstants
import com.jaffa.rpc.lib.entities.Command
import com.jaffa.rpc.lib.http.receivers.HttpAsyncAndSyncRequestReceiver
import com.jaffa.rpc.lib.zookeeper.Utils
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.sun.net.httpserver.HttpsServer
import io.prometheus.client.Gauge
import io.prometheus.client.exporter.HTTPServer
import org.apache.commons.collections4.QueueUtils
import org.apache.commons.collections4.queue.CircularFifoQueue
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.DependsOn
import org.springframework.stereotype.Component
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.security.KeyManagementException
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.UnrecoverableKeyException
import java.security.cert.CertificateException
import java.util.*
import java.util.concurrent.Executors
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy

@Component
@DependsOn("jaffaService")
class AdminServer {
    private val log = LoggerFactory.getLogger(AdminServer::class.java)

    private var server: HttpServer? = null

    private var prometheusServer: HTTPServer? = null

    @Throws(IOException::class)
    private fun respondWithFile(exchange: HttpExchange, fileName: String) {
        val classloader = Thread.currentThread().contextClassLoader
        val inputStream = classloader.getResourceAsStream(fileName)
                ?: throw IOException("No such file in resources: $fileName")
        val page = ByteStreams.toByteArray(inputStream)
        exchange.sendResponseHeaders(200, page.size.toLong())
        val os = exchange.responseBody
        os.write(page)
        os.close()
        exchange.close()
    }

    @Throws(IOException::class)
    private fun respondWithString(exchange: HttpExchange, response: String) {
        exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
        val os = exchange.responseBody
        os.write(response.toByteArray())
        os.close()
        exchange.close()
    }

    @get:Throws(IOException::class)
    private val freePort: Int
        get() {
            ServerSocket(0).use { socket -> return socket.localPort }
        }

    @PostConstruct
    fun init() {
        try {
            val useHttps = System.getProperty(OptionConstants.ADMIN_USE_HTTPS, false.toString()).toBoolean()
            prometheusServer = HTTPServer(freePort)
            log.info("Prometheus metrics are published on port {}", prometheusServer?.port)
            server = if (useHttps) {
                val httpsServer = HttpsServer.create(InetSocketAddress(Utils.localHost, freePort), 0)
                HttpAsyncAndSyncRequestReceiver.initSSLForHttpsServer(httpsServer,
                        Utils.getRequiredOption(OptionConstants.ADMIN_SSL_TRUSTSTORE_LOCATION),
                        Utils.getRequiredOption(OptionConstants.ADMIN_SSL_KEYSTORE_LOCATION),
                        Utils.getRequiredOption(OptionConstants.ADMIN_SSL_TRUSTSTORE_PASSWORD),
                        Utils.getRequiredOption(OptionConstants.ADMIN_SSL_KEYSTORE_PASSWORD))
                httpsServer
            } else {
                HttpServer.create(InetSocketAddress(Utils.localHost, freePort), 0)
            }
            server?.createContext("/") { exchange: HttpExchange ->
                when (exchange.requestURI.path) {
                    "/admin" -> {
                        respondWithFile(exchange, "admin.html")
                    }
                    "/vis.min.css" -> {
                        respondWithFile(exchange, "vis.min.css")
                    }
                    "/vis.min.js" -> {
                        respondWithFile(exchange, "vis.min.js")
                    }
                    "/protocol" -> {
                        Utils.rpcProtocol?.fullName?.let { respondWithString(exchange, it) }
                    }
                    "/response" -> {
                        var count = 0
                        val builder = StringBuilder()
                        var metric: ResponseMetric?
                        do {
                            metric = responses.poll()
                            if (metric != null) {
                                count++
                                builder.append(metric.time).append(':').append(metric.duration).append(';')
                            }
                        } while (metric != null && count < 30)
                        respondWithString(exchange, builder.toString())
                    }
                    else -> {
                        respondWithString(exchange, "OK")
                    }
                }
            }
            server?.executor = Executors.newFixedThreadPool(3)
            server?.start()
            log.info("Jaffa RPC console started at {}", (if (useHttps) "https://" else "http://") + server?.address?.hostName + ":" + server?.address?.port + "/admin")
        } catch (httpServerStartupException: IOException) {
            log.error("Exception during admin HTTP server startup", httpServerStartupException)
        } catch (httpServerStartupException: KeyStoreException) {
            log.error("Exception during admin HTTP server startup", httpServerStartupException)
        } catch (httpServerStartupException: NoSuchAlgorithmException) {
            log.error("Exception during admin HTTP server startup", httpServerStartupException)
        } catch (httpServerStartupException: CertificateException) {
            log.error("Exception during admin HTTP server startup", httpServerStartupException)
        } catch (httpServerStartupException: UnrecoverableKeyException) {
            log.error("Exception during admin HTTP server startup", httpServerStartupException)
        } catch (httpServerStartupException: KeyManagementException) {
            log.error("Exception during admin HTTP server startup", httpServerStartupException)
        }
    }

    @PreDestroy
    fun destroy() {
        server?.stop(2)
        prometheusServer?.stop()
    }

    class ResponseMetric(val time: Long, val duration: Double)

    companion object {
        private val log = LoggerFactory.getLogger(AdminServer::class.java)

        private val responses: Queue<ResponseMetric> = QueueUtils.synchronizedQueue(CircularFifoQueue(1000))

        private val requestLatency: Gauge = Gauge.build()
                .name("requests_latency_seconds").help("Request latency in ms.").register()

        @kotlin.jvm.JvmStatic
        fun addMetric(command: Command) {
            val executionDuration = (System.nanoTime() - command.localRequestTime) / 1000000.0
            requestLatency.set(executionDuration)
            log.debug(">>>>>> Executed request {} in {} ms", command.rqUid, executionDuration)
            responses.add(ResponseMetric(command.requestTime, executionDuration))
        }
    }
}