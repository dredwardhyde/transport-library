package com.jaffa.rpc.test;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.Assert;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.io.File;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@SuppressWarnings("squid:S2187")
public class TestServer {

    private static final boolean loadTest = false;

    private static String getClassPathFromParent() {
        return System.getProperty("java.class.path", "./*");
    }

    private static String getJavaCmdFromParent() {
        return Objects.isNull(System.getProperty("java.home")) ? "java" : String.format("%s%sbin%sjava", System.getProperty("java.home"), File.separator, File.separator);
    }

    public static void main(String... args) {
        log.info("================ TEST SERVER STARTING ================");

        System.setProperty("jaffa-rpc-config", "./jaffa-rpc-config-test-server.properties");

        final AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.register(MainConfig.class);
        ctx.refresh();

        Runtime.getRuntime().addShutdownHook(new Thread(ctx::close));

        PersonServiceClient personService = ctx.getBean(PersonServiceClient.class);
        ClientServiceClient clientService = ctx.getBean(ClientServiceClient.class);

        if (!loadTest) {
            Integer id = personService.add("Test name", "test@mail.com", null)
                    .withTimeout(15, TimeUnit.SECONDS)
                    .onModule("test.server")
                    .executeSync();
            log.info("Resulting id is {}", id);
            Person person = personService.get(id)
                    .onModule("test.server")
                    .executeSync();
            Assert.assertEquals(person.getId(), id);
            log.info(person.toString());
            personService.lol().executeSync();
            personService.lol2("kek").executeSync();
            String name = personService.getName().executeSync();
            log.info("Name: {}", name);
            Assert.assertNull(name);
            clientService.lol3("test3")
                    .onModule("test.server")
                    .executeSync();
            clientService.lol4("test4")
                    .onModule("test.server")
                    .executeSync();
            clientService.lol4("test4")
                    .onModule("test.server")
                    .withTimeout(10, TimeUnit.SECONDS)
                    .executeAsync(UUID.randomUUID().toString(), ServiceCallback.class);
            personService.get(id)
                    .onModule("test.server")
                    .executeAsync(UUID.randomUUID().toString(), PersonCallback.class);
            personService.lol2("kek").executeSync();
            try {
                personService.testError()
                        .onModule("test.server")
                        .executeSync();
            } catch (Throwable e) {
                log.error("Exception during sync call:", e);
                Assert.assertTrue(e.getMessage().contains("very bad in") || (e.getCause() != null && e.getCause().getMessage().contains("very bad in")));
            }
            personService.testError().onModule("test.server").executeAsync(UUID.randomUUID().toString(), PersonCallback.class);
        } else {
            // 1 hour load test
            final boolean sync = true;
            final boolean heavy = false;
            Runnable runnable = () -> {
                long startTime = System.currentTimeMillis();
                while (!Thread.currentThread().isInterrupted() && (System.currentTimeMillis() - startTime) < (60 * 60 * 1000)) {
                    if (sync) {
                        if (heavy) {
                            personService.getHeavy(RandomStringUtils.randomAlphabetic(250_000))
                                    .onModule("test.server")
                                    .executeSync();
                        } else {
                            // Sync call
                            clientService.lol3("test3")
                                    .onModule("test.server")
                                    .executeSync();
                        }
                    } else {
                        // Async call
                        clientService.lol3("test3")
                                .onModule("test.server")
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
        }

        final String javaCmd = getJavaCmdFromParent();
        final String classpath = getClassPathFromParent();
        final ProcessBuilder proc = new ProcessBuilder(javaCmd, "-cp", classpath, MainServer.class.getName());
        proc.redirectErrorStream(true);
        proc.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        try {
            Process process = proc.start();
            int returnCode = process.waitFor();
            log.info("Main test server returned {}", returnCode);
        }catch (Exception e){
            log.error("Exception while launching main.server", e);
        }
        ctx.close();
        System.exit(0);
    }
}
