package com.jaffa.rpc.lib.http

import com.google.common.io.ByteStreams
import com.jaffa.rpc.lib.entities.Protocol
import com.jaffa.rpc.lib.exception.JaffaRpcExecutionException
import com.jaffa.rpc.lib.exception.JaffaRpcExecutionTimeoutException
import com.jaffa.rpc.lib.exception.JaffaRpcNoRouteException
import com.jaffa.rpc.lib.http.receivers.HttpAsyncAndSyncRequestReceiver
import com.jaffa.rpc.lib.request.Sender
import com.jaffa.rpc.lib.zookeeper.Utils
import lombok.extern.slf4j.Slf4j
import org.apache.http.HttpEntity
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpPost
import org.apache.http.conn.ConnectTimeoutException
import org.apache.http.entity.ByteArrayEntity
import org.slf4j.LoggerFactory
import java.net.SocketTimeoutException

@Slf4j
class HttpRequestSender : Sender() {

    private val log = LoggerFactory.getLogger(HttpAsyncAndSyncRequestReceiver::class.java)

    public override fun executeSync(message: ByteArray?): ByteArray? {
        return try {
            val totalTimeout = (if (timeout == -1L) 1000 * 60 * 60 else timeout).toInt()
            val config = RequestConfig.custom()
                    .setConnectTimeout(totalTimeout)
                    .setConnectionRequestTimeout(totalTimeout)
                    .setSocketTimeout(totalTimeout).build()
            val httpPost = HttpPost(Utils.getHostForService(command?.serviceClass, moduleId, Protocol.HTTP).left.toString() + "/request")
            httpPost.config = config
            val postParams: HttpEntity = ByteArrayEntity(message)
            httpPost.entity = postParams
            val httpResponse: CloseableHttpResponse = try {
                HttpAsyncAndSyncRequestReceiver.client.execute(httpPost)
            } catch (e: ConnectTimeoutException) {
                throw JaffaRpcExecutionTimeoutException()
            } catch (e: SocketTimeoutException) {
                throw JaffaRpcExecutionTimeoutException()
            }
            val response = httpResponse.statusLine.statusCode
            if (response != 200) {
                httpResponse.close()
                throw JaffaRpcExecutionException("Response for RPC request " + command?.rqUid + " returned status " + response)
            }
            val responseBody = httpResponse.entity.content
            val byteArray = ByteStreams.toByteArray(responseBody)
            httpResponse.close()
            byteArray
        } catch (exception: JaffaRpcNoRouteException) {
            throw exception
        } catch (exception: JaffaRpcExecutionTimeoutException) {
            throw exception
        } catch (e: Exception) {
            log.error("Error while sending sync HTTP request", e)
            throw JaffaRpcExecutionException(e)
        }
    }

    public override fun executeAsync(message: ByteArray?) {
        try {
            val httpPost = HttpPost(Utils.getHostForService(command?.serviceClass, moduleId, Protocol.HTTP).left.toString() + "/request")
            val postParams: HttpEntity = ByteArrayEntity(message)
            httpPost.entity = postParams
            val httpResponse = HttpAsyncAndSyncRequestReceiver.client.execute(httpPost)
            val response = httpResponse.statusLine.statusCode
            httpResponse.close()
            if (response != 200) {
                throw JaffaRpcExecutionException("Response for RPC request " + command?.rqUid + " returned status " + response)
            }
        } catch (exception: JaffaRpcNoRouteException) {
            throw exception
        } catch (exception: JaffaRpcExecutionTimeoutException) {
            throw exception
        } catch (e: Exception) {
            log.error("Error while sending async HTTP request", e)
            throw JaffaRpcExecutionException(e)
        }
    }
}