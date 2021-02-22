package com.jaffa.rpc.test.http;

import com.jaffa.rpc.lib.common.OptionConstants;
import com.jaffa.rpc.lib.exception.JaffaRpcNoRouteException;
import com.jaffa.rpc.lib.exception.JaffaRpcSystemException;
import com.jaffa.rpc.lib.http.HttpRequestSender;
import com.jaffa.rpc.lib.http.receivers.HttpAsyncAndSyncRequestReceiver;
import com.jaffa.rpc.lib.http.receivers.HttpAsyncResponseReceiver;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
@SuppressWarnings({"squid:S5786"})
public class HttpSecondTest {

    static {
        System.setProperty("jaffa.rpc.test.server.protocol", "http");
        System.setProperty("jaffa.rpc.test.server.protocol.use.https", "true");
        System.setProperty("jaffa.rpc.test.server.protocol.http.ssl.server.truststore.location", "src/test/resources/truststore.jks");
        System.setProperty("jaffa.rpc.test.server.protocol.http.ssl.server.truststore.password", "simulator1");
        System.setProperty("jaffa.rpc.test.server.protocol.http.ssl.server.keystore.location", "src/test/resources/keystore.jks");
        System.setProperty("jaffa.rpc.test.server.protocol.http.ssl.server.keystore.password", "simulator1");
        System.setProperty("jaffa.rpc.test.server.protocol.http.ssl.client.truststore.location", "src/test/resources/truststore.jks");
        System.setProperty("jaffa.rpc.test.server.protocol.http.ssl.client.truststore.password", "simulator1");
        System.setProperty("jaffa.rpc.test.server.protocol.http.ssl.client.keystore.location", "src/test/resources/keystore.jks");
        System.setProperty("jaffa.rpc.test.server.protocol.http.ssl.client.keystore.password", "simulator1");
    }

    @Test
    public void stage1() {
        OptionConstants.setModuleId("test.server");
        HttpAsyncAndSyncRequestReceiver httpAsyncAndSyncRequestReceiver = new HttpAsyncAndSyncRequestReceiver();
        try {
            HttpAsyncAndSyncRequestReceiver.initClient();
            fail();
        } catch (JaffaRpcSystemException jaffaRpcSystemException) {
            //No-op
        }
        try {
            httpAsyncAndSyncRequestReceiver.run();
            fail();
        } catch (JaffaRpcSystemException jaffaRpcSystemException) {
            //No-op
        }
        HttpRequestSender httpRequestSender = new HttpRequestSender();
        try {
            httpRequestSender.executeAsync(new byte[]{});
            fail();
        } catch (JaffaRpcNoRouteException jaffaRpcNoRouteException) {
            //No-op
        }
        try {
            httpRequestSender.executeSync(new byte[]{});
            fail();
        } catch (JaffaRpcNoRouteException jaffaRpcExecutionException) {
            //No-op
        }
        HttpAsyncResponseReceiver httpAsyncResponseReceiver = new HttpAsyncResponseReceiver();
        try {
            httpAsyncResponseReceiver.run();
            fail();
        } catch (JaffaRpcSystemException jaffaRpcSystemException) {
            //No-op
        }

        HttpAsyncResponseReceiver.HttpRequestHandler httpRequestHandler = new HttpAsyncResponseReceiver.HttpRequestHandler();
        httpRequestHandler.handle(null);
    }
}
