package com.jaffa.rpc.test;

import com.jaffa.rpc.test.callbacks.ServiceCallback;
import com.jaffa.rpc.test.services.ClientServiceClient;
import com.jaffa.rpc.test.services.PersonServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.RandomStringUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@SuppressWarnings({"squid:S2187", "squid:S2925", "ConstantConditions"})
public class LoadTest {
    public static void main(String[] args) {
        log.info("================ TEST SERVER STARTING ================");

        System.setProperty("jaffa-rpc-config", "./jaffa-rpc-config-load-server.properties");
        System.setProperty("module.id", "load.server");
        final AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.register(MainConfig.class);
        ctx.refresh();

        Runtime.getRuntime().addShutdownHook(new Thread(ctx::close));

        PersonServiceClient personService = ctx.getBean(PersonServiceClient.class);
        ClientServiceClient clientService = ctx.getBean(ClientServiceClient.class);

        // 1 hour load test
        final boolean sync = true;
        final boolean heavy = false;
        Runnable runnable = () -> {
            long startTime = System.currentTimeMillis();
            while (!Thread.currentThread().isInterrupted() && (System.currentTimeMillis() - startTime) < (60 * 60 * 1000)) {
                if (sync) {
                    if (heavy) {
                        personService.getHeavy(RandomStringUtils.randomAlphabetic(250_000))
                                .onModule("load.server")
                                .executeSync();
                    } else {
                        // Sync call
                        clientService.lol3("test3")
                                .onModule("load.server")
                                .executeSync();
                    }
                } else {
                    // Async call
                    clientService.lol3("test3")
                            .onModule("load.server")
                            .withTimeout(10, TimeUnit.SECONDS)
                            .executeAsync(UUID.randomUUID().toString(), ServiceCallback.class);
                }
                try {
                    Thread.sleep((int) (Math.random() * 100));
                } catch (InterruptedException exception) {
                    exception.printStackTrace();
                }
            }
        };
        try {
            Thread thread = new Thread(runnable);
            thread.start();
            thread.join();
        } catch (Exception ignore) {
        }

        ctx.close();
        System.exit(0);
    }
}
