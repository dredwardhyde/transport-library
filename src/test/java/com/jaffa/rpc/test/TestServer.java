package com.jaffa.rpc.test;

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.io.File;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@SuppressWarnings("squid:S2187")
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {MainConfig.class}, loader = AnnotationConfigContextLoader.class)
public class TestServer {

    static {
        System.setProperty("jaffa-rpc-config", "./jaffa-rpc-config-test-server.properties");
    }

    @Autowired
    private PersonServiceClient personService;

    @Autowired
    ClientServiceClient clientService;

    private static String getClassPathFromParent() {
        return System.getProperty("java.class.path", "./*");
    }

    private static String getJavaCmdFromParent() {
        return Objects.isNull(System.getProperty("java.home")) ? "java" : String.format("%s%sbin%sjava", System.getProperty("java.home"), File.separator, File.separator);
    }

    @Test
    public void stage_1() {
        log.info("Started {}", new Object() {
        }.getClass().getEnclosingMethod().getName());
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
    }

    @Test
    public void stage_2() {
        log.info("Started {}", new Object() {
        }.getClass().getEnclosingMethod().getName());
        final String javaCmd = getJavaCmdFromParent();
        final String classpath = getClassPathFromParent();
        final ProcessBuilder proc = new ProcessBuilder(javaCmd, "-cp", classpath, MainServer.class.getName());
        proc.redirectErrorStream(true);
        proc.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        try {
            Process process = proc.start();
            int returnCode = process.waitFor();
            log.info("Main test server returned {}", returnCode);
            Assert.assertEquals(0, returnCode);
        } catch (Exception e) {
            log.error("Exception while launching main.server", e);
        }
    }
}
