package com.jaffa.rpc.test.admin;

import com.jaffa.rpc.lib.common.OptionConstants;
import com.jaffa.rpc.lib.entities.Command;
import com.jaffa.rpc.lib.http.receivers.HttpAsyncAndSyncRequestReceiver;
import com.jaffa.rpc.lib.ui.AdminServer;
import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.UUID;

@Slf4j
@SuppressWarnings({"squid:S5786"})
public class AdminServerSecondTest {

    static {
        System.setProperty("jaffa.test.server.admin.use.https", "false");
        System.setProperty("jaffa.rpc.test.server.protocol.use.https", "false");
        System.setProperty("jaffa.rpc.test.server.protocol", "http");
    }

    private String prefix = null;
    private AdminServer adminServer = null;
    private CloseableHttpClient client = null;

    @BeforeEach
    public void setUp() throws Exception {
        OptionConstants.setModuleId("test.server");
        HttpAsyncAndSyncRequestReceiver.initClient();
        client = HttpAsyncAndSyncRequestReceiver.client;
        adminServer = new AdminServer();
        adminServer.init();
        Field f = AdminServer.class.getDeclaredField("server");
        f.setAccessible(true);
        Object a = f.get(adminServer);
        Assertions.assertNotNull(a);
        HttpServer httpServer = (HttpServer) a;
        InetSocketAddress serverAddress = httpServer.getAddress();
        log.info("Admin UI started at host:{} and port:{}", serverAddress.getHostName(), serverAddress.getPort());
        prefix = "http://" + serverAddress.getHostName() + ":" + serverAddress.getPort();
    }

    @Test
    public void stage1() throws Exception {
        checkUrl("/admin");
        checkUrl("/vis.min.css");
        checkUrl("/vis.min.js");
        checkUrl("/protocol");
        Command command = new Command();
        command.setLocalRequestTime(System.nanoTime() - 10_000);
        command.setRqUid(UUID.randomUUID().toString());
        command.setRequestTime(System.currentTimeMillis() - 10_000);
        AdminServer.addMetric(command);
        checkUrl("/response");
        checkUrl("/xxx");
    }

    private void checkUrl(String path) throws IOException {
        HttpGet httpGet = new HttpGet(prefix + path);
        CloseableHttpResponse httpResponse = client.execute(httpGet);
        httpResponse.close();
        Assertions.assertEquals(200, httpResponse.getStatusLine().getStatusCode());
    }

    @AfterEach
    public void tearDown() {
        adminServer.destroy();
        try {
            HttpAsyncAndSyncRequestReceiver.client.close();
        } catch (IOException e) {
            log.error("Error while closing HTTP client", e);
        }
    }
}
