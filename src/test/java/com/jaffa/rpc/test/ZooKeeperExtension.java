package com.jaffa.rpc.test;

import lombok.extern.slf4j.Slf4j;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class ZooKeeperExtension implements AfterAllCallback, BeforeAllCallback {

    private static TestingServer zkServer;

    private static final AtomicInteger refCount = new AtomicInteger(0);

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        if (refCount.get() == 0) {
            log.info("Starting ZooKeeper server");
            zkServer = new TestingServer(2181, Files.createTempDirectory("zk-dir-" + System.currentTimeMillis()).toFile());
        }
        refCount.incrementAndGet();
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        int result = refCount.decrementAndGet();
        if (result == 0) {
            log.info("Shutting down ZooKeeper server");
            zkServer.close();
        }
    }
}