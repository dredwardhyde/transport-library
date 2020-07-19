package com.jaffa.rpc.test.servers;

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

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SuppressWarnings("squid:S2187")
@ExtendWith({ZooKeeperExtension.class, SpringExtension.class})
@ContextConfiguration(classes = {MainConfig.class}, loader = AnnotationConfigContextLoader.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractLeaderTestServer {

    static {
        System.setProperty("jaffa.rpc.test.mode", "true");
        System.setProperty("jaffa.rpc.module.id", "test.server");
        System.setProperty("jaffa.rpc.zookeeper.connection", "localhost:2181");
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
    void stage1() {
        log.info("Started {}", new Object() {
        }.getClass().getEnclosingMethod().getName());
        Integer id = personService.add("Test name", "test@mail.com", null)
                .withTimeout(15, TimeUnit.SECONDS)
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
            assertTrue(e.getMessage().contains("very bad in") || (Objects.nonNull(e.getCause()) && e.getCause().getMessage().contains("very bad in")));
        }
        personService.testError().onModule("test.server").executeAsync(UUID.randomUUID().toString(), PersonCallback.class);
    }

    @Test
    void stage2() {
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
