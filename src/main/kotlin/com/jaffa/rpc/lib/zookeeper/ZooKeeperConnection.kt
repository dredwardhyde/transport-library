package com.jaffa.rpc.lib.zookeeper

import com.jaffa.rpc.lib.common.OptionConstants
import org.apache.zookeeper.WatchedEvent
import org.apache.zookeeper.Watcher
import org.apache.zookeeper.ZooKeeper
import org.apache.zookeeper.client.ZKClientConfig
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.CountDownLatch

class ZooKeeperConnection {

    private val log = LoggerFactory.getLogger(ZooKeeperConnection::class.java)

    private val connectedSignal = CountDownLatch(1)

    private lateinit var zoo: ZooKeeper

    @Throws(IOException::class, InterruptedException::class)
    fun connect(host: String?): ZooKeeper {
        if (zkConfig == null) {
            val zkClientConfig = ZKClientConfig()
            zkClientConfig.setProperty("zookeeper.clientCnxnSocket", System.getProperty(OptionConstants.ZOOKEEPER_CLIENT_CONTEXT, "org.apache.zookeeper.ClientCnxnSocketNetty"))
            zkClientConfig.setProperty("zookeeper.client.secure", System.getProperty(OptionConstants.ZOOKEEPER_CLIENT_SECURE, false.toString()))
            if (System.getProperty(OptionConstants.ZOOKEEPER_CLIENT_SECURE, false.toString()).toBoolean()) {
                zkClientConfig.setProperty("zookeeper.ssl.keyStore.location", Utils.getRequiredOption(OptionConstants.ZOOKEEPER_SSL_KEYSTORE_LOCATION))
                zkClientConfig.setProperty("zookeeper.ssl.keyStore.password", Utils.getRequiredOption(OptionConstants.ZOOKEEPER_SSL_KEYSTORE_PASSWORD))
                zkClientConfig.setProperty("zookeeper.ssl.trustStore.location", Utils.getRequiredOption(OptionConstants.ZOOKEEPER_SSL_TRUSTSTORE_LOCATION))
                zkClientConfig.setProperty("zookeeper.ssl.trustStore.password", Utils.getRequiredOption(OptionConstants.ZOOKEEPER_SSL_TRUSTSTORE_PASSWORD))
            }
            zkConfig = zkClientConfig
        }
        zoo = ZooKeeper(host, 5000, { watchedEvent: WatchedEvent ->
            if (watchedEvent.state == Watcher.Event.KeeperState.SyncConnected) {
                connectedSignal.countDown()
            }
            if (watchedEvent.type == Watcher.Event.EventType.NodeDataChanged) {
                Utils.cache.invalidate(watchedEvent.path)
                log.info("Service {} changed for instance {} ", watchedEvent.path, OptionConstants.MODULE_ID)
            }
        }, zkConfig)
        connectedSignal.await()
        return zoo
    }

    @Throws(InterruptedException::class)
    fun close() {
        zoo.close()
    }

    companion object {
        var zkConfig: ZKClientConfig? = null
    }
}