package com.jaffa.rpc.lib.http;

import com.google.common.io.ByteStreams;
import com.jaffa.rpc.lib.entities.Protocol;
import com.jaffa.rpc.lib.exception.JaffaRpcExecutionException;
import com.jaffa.rpc.lib.exception.JaffaRpcExecutionTimeoutException;
import com.jaffa.rpc.lib.http.receivers.HttpAsyncAndSyncRequestReceiver;
import com.jaffa.rpc.lib.request.Sender;
import com.jaffa.rpc.lib.zookeeper.Utils;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.entity.ByteArrayEntity;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;

@Slf4j
public class HttpRequestSender extends Sender {

    @Override
    public byte[] executeSync(byte[] message) {
        try {
            int totalTimeout = (int)(this.timeout == -1 ? 1000 * 60 * 60 : this.timeout);
            RequestConfig config = RequestConfig.custom()
                    .setConnectTimeout(totalTimeout)
                    .setConnectionRequestTimeout(totalTimeout)
                    .setSocketTimeout(totalTimeout).build();
            HttpPost httpPost = new HttpPost(Utils.getHostForService(command.getServiceClass(), moduleId, Protocol.HTTP).getLeft() + "/request");
            httpPost.setConfig(config);
            HttpEntity postParams = new ByteArrayEntity(message);
            httpPost.setEntity(postParams);
            CloseableHttpResponse httpResponse;
            try {
                httpResponse = HttpAsyncAndSyncRequestReceiver.getClient().execute(httpPost);
            } catch (ConnectTimeoutException | SocketTimeoutException e) {
                throw new JaffaRpcExecutionTimeoutException();
            }
            int response = httpResponse.getStatusLine().getStatusCode();
            if (response != 200) {
                httpResponse.close();
                throw new JaffaRpcExecutionException("Response for RPC request " + command.getRqUid() + " returned status " + response);
            }
            InputStream responseBody = httpResponse.getEntity().getContent();
            byte[] byteArray = ByteStreams.toByteArray(responseBody);
            httpResponse.close();
            return byteArray;
        } catch (IOException e) {
            log.error("Error while sending sync HTTP request", e);
            throw new JaffaRpcExecutionException(e);
        }
    }

    @Override
    public void executeAsync(byte[] message) {
        try {
            HttpPost httpPost = new HttpPost(Utils.getHostForService(command.getServiceClass(), moduleId, Protocol.HTTP).getLeft() + "/request");
            HttpEntity postParams = new ByteArrayEntity(message);
            httpPost.setEntity(postParams);
            CloseableHttpResponse httpResponse = HttpAsyncAndSyncRequestReceiver.getClient().execute(httpPost);
            int response = httpResponse.getStatusLine().getStatusCode();
            httpResponse.close();
            if (response != 200) {
                throw new JaffaRpcExecutionException("Response for RPC request " + command.getRqUid() + " returned status " + response);
            }
        } catch (IOException e) {
            log.error("Error while sending async HTTP request", e);
            throw new JaffaRpcExecutionException(e);
        }
    }
}
