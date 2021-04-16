package com.jaffa.rpc.lib.http.receivers

import com.google.common.io.ByteStreams
import com.jaffa.rpc.lib.common.OptionConstants
import com.jaffa.rpc.lib.common.RequestInvocationHelper
import com.jaffa.rpc.lib.entities.Command
import com.jaffa.rpc.lib.exception.JaffaRpcExecutionException
import com.jaffa.rpc.lib.exception.JaffaRpcSystemException
import com.jaffa.rpc.lib.serialization.Serializer
import com.jaffa.rpc.lib.zookeeper.Utils
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsParameters
import com.sun.net.httpserver.HttpsServer
import org.apache.http.client.methods.HttpPost
import org.apache.http.config.RegistryBuilder
import org.apache.http.conn.socket.ConnectionSocketFactory
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.conn.ssl.TrustSelfSignedStrategy
import org.apache.http.entity.ByteArrayEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.ssl.SSLContexts
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.FileInputStream
import java.io.IOException
import java.security.KeyManagementException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.UnrecoverableKeyException
import java.security.cert.CertificateException
import java.util.concurrent.Executors
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

class HttpAsyncAndSyncRequestReceiver : Runnable, Closeable {

    private var server: HttpServer? = null

    override fun run() {
        try {
            server = if (System.getProperty(OptionConstants.USE_HTTPS, false.toString()).toBoolean()) {
                val httpsServer = HttpsServer.create(Utils.httpBindAddress, 0)
                initSSLForHttpsServer(
                        httpsServer,
                        Utils.getRequiredOption(OptionConstants.HTTP_SSL_SERVER_TRUSTSTORE_LOCATION),
                        Utils.getRequiredOption(OptionConstants.HTTP_SSL_SERVER_KEYSTORE_LOCATION),
                        Utils.getRequiredOption(OptionConstants.HTTP_SSL_SERVER_TRUSTSTORE_PASSWORD),
                        Utils.getRequiredOption(OptionConstants.HTTP_SSL_SERVER_KEYSTORE_PASSWORD)
                )
                httpsServer
            } else {
                HttpServer.create(Utils.httpBindAddress, 0)
            }
                    .also { it.createContext("/request", HttpRequestHandler()) }
                    .also { it.executor = Executors.newFixedThreadPool(9) }
                    .also { it.start() }
        } catch (httpServerStartupException: Exception) {
            log.error("Error during HTTP request receiver startup:", httpServerStartupException)
            throw JaffaRpcSystemException(httpServerStartupException)
        }
        log.info("{} started", this.javaClass.simpleName)
    }

    override fun close() {
        server?.stop(2)
        service.shutdown()
        try {
            client.close()
        } catch (e: IOException) {
            log.error("Error while closing HTTP client", e)
        }
        log.info("HTTP request receiver stopped")
    }

    class HttpRequestHandler : HttpHandler {
        private val log = LoggerFactory.getLogger(HttpRequestHandler::class.java)

        @Throws(IOException::class)
        override fun handle(request: HttpExchange) {
            val command = Serializer.current.deserialize(ByteStreams.toByteArray(request.requestBody), Command::class.java)
            if (command?.callbackKey != null && command.callbackClass != null) {
                val response = "OK"
                request.sendResponseHeaders(200, response.toByteArray().size.toLong())
                val os = request.responseBody
                os.write(response.toByteArray())
                os.close()
                request.close()
                val runnable = Runnable {
                    try {
                        val httpResponse = client.execute(HttpPost(command.callBackHost + "/response")
                                .also {
                                    it.entity = ByteArrayEntity(Serializer.current.serialize(RequestInvocationHelper
                                            .constructCallbackContainer(command, RequestInvocationHelper.invoke(command))))
                                })
                        val callBackResponse = httpResponse.statusLine.statusCode
                        httpResponse.close()
                        if (callBackResponse != 200) {
                            throw JaffaRpcExecutionException("Response for RPC request " + command.rqUid + " returned status " + response)
                        }
                    } catch (exception: Exception) {
                        log.error("Error while receiving async request", exception)
                    }
                }
                service.execute(runnable)
            } else {
                try {
                    val response = Serializer.current.serializeWithClass(RequestInvocationHelper.getResult(command?.let { RequestInvocationHelper.invoke(it) }))
                    response?.size?.toLong()?.let { request.sendResponseHeaders(200, it) }
                    val os = request.responseBody
                    os.write(response)
                    os.close()
                    request.close()
                } catch (exception: Exception) {
                    log.error("Error while receiving sync request", exception)
                }
            }
        }
    }

    companion object {

        private val service = Executors.newFixedThreadPool(3)

        private val log = LoggerFactory.getLogger(HttpAsyncAndSyncRequestReceiver::class.java)

        lateinit var client: CloseableHttpClient

        @kotlin.jvm.JvmStatic
        fun initClient() {
            if (System.getProperty(OptionConstants.USE_HTTPS, false.toString()).toBoolean()) {
                val sslContext: SSLContext = try {
                    val keyPassphrase = Utils.getRequiredOption(OptionConstants.HTTP_SSL_CLIENT_KEYSTORE_PASSWORD).toCharArray()
                    val ks = KeyStore.getInstance("JKS")
                    ks.load(FileInputStream(Utils.getRequiredOption(OptionConstants.HTTP_SSL_CLIENT_KEYSTORE_LOCATION)), keyPassphrase)
                    val trustPassphrase = Utils.getRequiredOption(OptionConstants.HTTP_SSL_CLIENT_TRUSTSTORE_PASSWORD).toCharArray()
                    val tks = KeyStore.getInstance("JKS")
                    tks.load(FileInputStream(Utils.getRequiredOption(OptionConstants.HTTP_SSL_CLIENT_TRUSTSTORE_LOCATION)), trustPassphrase)
                    SSLContexts.custom().loadKeyMaterial(ks, keyPassphrase).loadTrustMaterial(tks, TrustSelfSignedStrategy.INSTANCE).build()
                } catch (e: Exception) {
                    log.error("Error occurred while creating HttpClient", e)
                    throw JaffaRpcSystemException(e)
                }
                val sslConnectionSocketFactory = SSLConnectionSocketFactory(sslContext, SSLConnectionSocketFactory.getDefaultHostnameVerifier())
                client = HttpClients.custom().setSSLSocketFactory(sslConnectionSocketFactory)
                        .setConnectionManager(PoolingHttpClientConnectionManager(RegistryBuilder.create<ConnectionSocketFactory>()
                                .register("https", sslConnectionSocketFactory)
                                .build()).also { it.maxTotal = 200 }).build()
            } else {
                client = HttpClients.custom().setConnectionManager(PoolingHttpClientConnectionManager().also { it.maxTotal = 200 }).build()
            }
        }

        @Throws(
                NoSuchAlgorithmException::class,
                KeyStoreException::class,
                IOException::class,
                CertificateException::class,
                UnrecoverableKeyException::class,
                KeyManagementException::class
        )
        fun initSSLForHttpsServer(
                httpsServer: HttpsServer,
                trustStoreLocation: String,
                keyStoreLocation: String,
                trustStorePassword: String,
                keyStorePassword: String
        ) {
            val keyPassphrase = keyStorePassword.toCharArray()
            val kmf = KeyManagerFactory.getInstance("SunX509")
            kmf.init(KeyStore.getInstance("JKS").also { it.load(FileInputStream(keyStoreLocation), keyPassphrase) }, keyPassphrase)
            val tmf = TrustManagerFactory.getInstance("SunX509")
            tmf.init(KeyStore.getInstance("JKS").also { it.load(FileInputStream(trustStoreLocation), trustStorePassword.toCharArray()) })
            val c = SSLContext.getInstance("TLSv1.2")
            c.init(kmf.keyManagers, tmf.trustManagers, null)
            httpsServer.httpsConfigurator = object : HttpsConfigurator(c) {
                override fun configure(params: HttpsParameters) {
                    try {
                        val c = SSLContext.getDefault()
                        val engine = c.createSSLEngine()
                        with(params) {
                            needClientAuth = true
                            cipherSuites = engine.enabledCipherSuites
                            protocols = engine.enabledProtocols
                            setSSLParameters(c.defaultSSLParameters)
                        }
                    } catch (ex: Exception) {
                        log.error("Failed to create Jaffa HTTPS server", ex)
                        throw JaffaRpcSystemException(ex)
                    }
                }
            }
        }
    }
}