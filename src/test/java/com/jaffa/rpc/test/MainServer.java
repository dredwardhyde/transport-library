package com.jaffa.rpc.test;

import com.jaffa.rpc.lib.exception.JaffaRpcExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@SuppressWarnings("squid:S2187")
public class MainServer {

    public static void main(String... args) {
        log.info("================ MAIN SERVER STARTING ================");

        System.setProperty("jaffa-rpc-config", "./jaffa-rpc-config-main-server.properties");

        final AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.register(MainConfig.class);
        ctx.refresh();

        Runtime.getRuntime().addShutdownHook(new Thread(ctx::close));

        PersonServiceClient personService = ctx.getBean(PersonServiceClient.class);
        ClientServiceClient clientService = ctx.getBean(ClientServiceClient.class);

        Runnable runnable = () -> {
            Integer id = personService.add("Test name 2", "test2@mail.com", null)
                    .withTimeout(15, TimeUnit.SECONDS)
                    .onModule("main.server")
                    .executeSync();
            log.info("Resulting id is {}", id);
            Person person = personService.get(id)
                    .onModule("main.server")
                    .executeSync();
            log.info(person.toString());
            personService.lol().executeSync();
            personService.lol2("kek").executeSync();
            log.info("Name: {}", personService.getName().executeSync());
            clientService.lol3("test3")
                    .onModule("main.server")
                    .executeSync();
            clientService.lol4("test4")
                    .onModule("main.server")
                    .executeSync();
            clientService.lol4("test4")
                    .onModule("main.server")
                    .executeAsync(UUID.randomUUID().toString(), ServiceCallback.class);
            personService.get(id)
                    .onModule("main.server")
                    .executeAsync(UUID.randomUUID().toString(), PersonCallback.class);
            personService.lol2("kek").executeSync();
            try {
                personService.testError()
                        .onModule("main.server")
                        .executeSync();
            } catch (Exception e) {
                log.error("Exception during sync call:", e);
            }
            personService.testError()
                    .onModule("main.server")
                    .executeAsync(UUID.randomUUID().toString(), PersonCallback.class);

            id = personService.add("Test name 2", "test2@mail.com", null)
                    .withTimeout(10, TimeUnit.SECONDS)
                    .onModule("test.server")
                    .executeSync();
            log.info("Resulting id is {}", id);
            person = personService.get(id)
                    .onModule("test.server")
                    .executeSync();
            log.info(person.toString());
            personService.lol().executeSync();
            personService.lol2("kek").executeSync();
            log.info("Name: {}", personService.getName().executeSync());
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
            } catch (JaffaRpcExecutionException e) {
                log.error("Exception during sync call:", e);
            }
            personService.testError()
                    .onModule("test.server")
                    .executeAsync(UUID.randomUUID().toString(), PersonCallback.class);
        };

        Thread thread1 = new Thread(runnable);
        Thread thread2 = new Thread(runnable);
        Thread thread3 = new Thread(runnable);

        thread1.start();
        thread2.start();
        thread3.start();

        try {
            thread1.join();
            thread2.join();
            thread3.join();
            Thread.sleep(TimeUnit.SECONDS.toMillis(20));
        } catch (Exception ignore) {
        }
        ctx.close();
        System.exit(0);
    }
}
