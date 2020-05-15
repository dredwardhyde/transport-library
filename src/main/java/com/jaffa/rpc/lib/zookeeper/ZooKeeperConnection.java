package com.jaffa.rpc.lib.zookeeper;

import lombok.Getter;
import lombok.Setter;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.client.ZKClientConfig;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

public class ZooKeeperConnection {

    @Getter
    @Setter
    private static ZKClientConfig zkConfig = null;
    private final CountDownLatch connectedSignal = new CountDownLatch(1);
    private ZooKeeper zoo;

    ZooKeeper connect(String host) throws IOException, InterruptedException {
        if (zkConfig == null) {
            ZKClientConfig zkClientConfig = new ZKClientConfig();
            zkClientConfig.setProperty("zookeeper.clientCnxnSocket", System.getProperty("jaffa.rpc.zookeeper.clientCnxnSocket", "org.apache.zookeeper.ClientCnxnSocketNetty"));
            zkClientConfig.setProperty("zookeeper.client.secure", System.getProperty("jaffa.rpc.zookeeper.client.secure", String.valueOf(false)));
            if (Boolean.parseBoolean(System.getProperty("jaffa.rpc.zookeeper.client.secure", String.valueOf(false)))) {
                zkClientConfig.setProperty("zookeeper.ssl.keyStore.location", Utils.getRequiredOption("jaffa.rpc.zookeeper.ssl.keyStore.location"));
                zkClientConfig.setProperty("zookeeper.ssl.keyStore.password", Utils.getRequiredOption("jaffa.rpc.zookeeper.ssl.keyStore.password"));
                zkClientConfig.setProperty("zookeeper.ssl.trustStore.location", Utils.getRequiredOption("jaffa.rpc.zookeeper.ssl.trustStore.location"));
                zkClientConfig.setProperty("zookeeper.ssl.trustStore.password", Utils.getRequiredOption("jaffa.rpc.zookeeper.ssl.trustStore.password"));
            }
            ZooKeeperConnection.setZkConfig(zkClientConfig);
        }
        zoo = new ZooKeeper(host, 5000, (watchedEvent -> {
            if (watchedEvent.getState() == Watcher.Event.KeeperState.SyncConnected) {
                connectedSignal.countDown();
            }
        }), zkConfig);
        connectedSignal.await();
        return zoo;
    }

    public void close() throws InterruptedException {
        zoo.close();
    }
}
