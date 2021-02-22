package com.jaffa.rpc.test.servers;

import com.jaffa.rpc.lib.exception.JaffaRpcExecutionTimeoutException;
import com.jaffa.rpc.lib.exception.JaffaRpcNoRouteException;
import com.jaffa.rpc.test.MainConfig;
import com.jaffa.rpc.test.ZooKeeperExtension;
import com.jaffa.rpc.test.callbacks.PersonCallback;
import com.jaffa.rpc.test.callbacks.ServiceCallback;
import com.jaffa.rpc.test.entities.Person;
import com.jaffa.rpc.test.services.ClientServiceClient;
import com.jaffa.rpc.test.services.PersonServiceClient;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.io.File;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
@SuppressWarnings({"squid:S2187", "squid:S5786"})
@ExtendWith({ZooKeeperExtension.class, SpringExtension.class})
@ContextConfiguration(classes = {MainConfig.class}, loader = AnnotationConfigContextLoader.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractLeaderTestServer {

    static {
        System.setProperty("jaffa.rpc.test.server.test.mode", "true");
        System.setProperty("module.id", "test.server");
        System.setProperty("jaffa.rpc.test.server.zookeeper.connection", "localhost:2181");
    }

    @Autowired
    private PersonServiceClient personService;

    @Autowired
    private ClientServiceClient clientService;

    @Getter
    @Setter
    private Class<?> follower;

    private static String getClassPathFromParent() {
        return System.getProperty("java.class.path", "./*");
    }

    private static String getJavaCmdFromParent() {
        return Objects.isNull(System.getProperty("java.home")) ? "java" : String.format("%s%sbin%sjava", System.getProperty("java.home"), File.separator, File.separator);
    }

    @Test
    public void stage1() {
        log.info("Started {}", new Object() {
        }.getClass().getEnclosingMethod().getName());
        Integer id = personService.add("Test name", "test@mail.com", null)
                .withTimeout(15L, TimeUnit.SECONDS)
                .onModule("test.server")
                .executeSync();
        Person person = personService.get(id)
                .onModule("test.server")
                .executeSync();
        assertEquals(person.getId(), id);
        personService.lol().executeSync();
        personService.lol2("kek").executeSync();
        String name = personService.getName().executeSync();
        assertNull(name);
        clientService.lol3("test3")
                .onModule("test.server")
                .executeSync();
        clientService.lol4("test4")
                .onModule("test.server")
                .executeSync();
        clientService.lol4("test4")
                .onModule("test.server")
                .withTimeout(10L, TimeUnit.SECONDS)
                .executeAsync(UUID.randomUUID().toString(), ServiceCallback.class);
        try {
            clientService.lol4("test4")
                    .onModule("test.server")
                    .withTimeout(5L, TimeUnit.SECONDS)
                    .executeSync();
            fail();
        } catch (JaffaRpcExecutionTimeoutException jaffaRpcExecutionTimeoutException) {
            log.info("Execution timeout exception occurred");
        }
        try {
            clientService.lol3("test3")
                    .onModule("lol.server")
                    .executeSync();
            fail();
        } catch (JaffaRpcNoRouteException jaffaRpcNoRouteException) {
            log.info("No route exception occurred");
        }
        try {
            clientService.lol3("test3")
                    .onModule("lol.server")
                    .executeAsync(UUID.randomUUID().toString(), ServiceCallback.class);
            fail();
        } catch (JaffaRpcNoRouteException jaffaRpcNoRouteException) {
            log.info("No route exception occurred");
        }
        personService.get(id)
                .onModule("test.server")
                .executeAsync(UUID.randomUUID().toString(), PersonCallback.class);
        personService.lol2("kek").executeSync();
        try {
            personService.testError()
                    .onModule("test.server")
                    .executeSync();
        } catch (Throwable e) {
            assertTrue(e.getMessage().contains("very bad in") || (Objects.nonNull(e.getCause()) && e.getCause().getMessage().contains("very bad in")));
        }
        personService.testError().onModule("test.server").executeAsync(UUID.randomUUID().toString(), PersonCallback.class);
    }

    @Test
    public void stage2() {
        log.info("Started {}", new Object() {
        }.getClass().getEnclosingMethod().getName());
        final String javaCmd = getJavaCmdFromParent();
        final String classpath = getClassPathFromParent();
        final ProcessBuilder proc = new ProcessBuilder(javaCmd, "-Djdk.tls.acknowledgeCloseNotify=true", "-cp", classpath, getFollower().getName());
        proc.redirectErrorStream(true);
        proc.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        try {
            Process process = proc.start();
            int returnCode = process.waitFor();
            log.info("Main test server returned {}", returnCode);
            assertEquals(0, returnCode);
        } catch (Exception e) {
            log.error("Exception while launching main.server", e);
        }
    }
}
