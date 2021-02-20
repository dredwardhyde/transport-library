package com.jaffa.rpc.lib.zookeeper;

import com.jaffa.rpc.lib.common.OptionConstants;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.client.ZKClientConfig;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

@Slf4j
public class ZooKeeperConnection {

    @Getter
    @Setter
    private static ZKClientConfig zkConfig = null;
    private final CountDownLatch connectedSignal = new CountDownLatch(1);
    private ZooKeeper zoo;

    public ZooKeeper connect(String host) throws IOException, InterruptedException {
        if (Objects.isNull(zkConfig)) {
            ZKClientConfig zkClientConfig = new ZKClientConfig();
            zkClientConfig.setProperty("zookeeper.clientCnxnSocket", System.getProperty(OptionConstants.ZOOKEEPER_CLIENT_CONTEXT, "org.apache.zookeeper.ClientCnxnSocketNetty"));
            zkClientConfig.setProperty("zookeeper.client.secure", System.getProperty(OptionConstants.ZOOKEEPER_CLIENT_SECURE, String.valueOf(false)));
            if (Boolean.parseBoolean(System.getProperty(OptionConstants.ZOOKEEPER_CLIENT_SECURE, String.valueOf(false)))) {
                zkClientConfig.setProperty("zookeeper.ssl.keyStore.location", Utils.getRequiredOption(OptionConstants.ZOOKEEPER_SSL_KEYSTORE_LOCATION));
                zkClientConfig.setProperty("zookeeper.ssl.keyStore.password", Utils.getRequiredOption(OptionConstants.ZOOKEEPER_SSL_KEYSTORE_PASSWORD));
                zkClientConfig.setProperty("zookeeper.ssl.trustStore.location", Utils.getRequiredOption(OptionConstants.ZOOKEEPER_SSL_TRUSTSTORE_LOCATION));
                zkClientConfig.setProperty("zookeeper.ssl.trustStore.password", Utils.getRequiredOption(OptionConstants.ZOOKEEPER_SSL_TRUSTSTORE_PASSWORD));
            }
            ZooKeeperConnection.setZkConfig(zkClientConfig);
        }
        zoo = new ZooKeeper(host, 5000, (watchedEvent -> {
            if (watchedEvent.getState() == Watcher.Event.KeeperState.SyncConnected) {
                connectedSignal.countDown();
            }
            if (watchedEvent.getType() == Watcher.Event.EventType.NodeDataChanged) {
                Utils.cache.invalidate(watchedEvent.getPath());
                log.info("Service {} changed for instance {} ", watchedEvent.getPath(), OptionConstants.MODULE_ID);
            }
        }), zkConfig);
        connectedSignal.await();
        return zoo;
    }

    public void close() throws InterruptedException {
        zoo.close();
    }
}
