package com.jaffa.rpc.test;

import lombok.extern.slf4j.Slf4j;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.nio.file.Files;

@Slf4j
public class ZooKeeperExtension implements AfterAllCallback, BeforeAllCallback {

    private static TestingServer zkServer;

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        log.info("Starting ZooKeeper server");
        zkServer = new TestingServer(2181, Files.createTempDirectory("zk-dir-" + System.currentTimeMillis()).toFile());
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        log.info("Shutting down ZooKeeper server");
        zkServer.close();
    }
}