package com.jaffa.rpc.lib.common;

import com.jaffa.rpc.lib.entities.Command;
import com.jaffa.rpc.lib.exception.JaffaRpcExecutionTimeoutException;
import com.jaffa.rpc.lib.exception.JaffaRpcSystemException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FinalizationWorker {

    @Getter
    private static final ConcurrentMap<String, Command> eventsToConsume = new ConcurrentHashMap<>();
    private static final CountDownLatch countDownLatch = new CountDownLatch(1);
    private static ApplicationContext context;
    private static final Thread finalizer = new Thread(() -> {
        log.info("Finalizer thread started");
        countDownLatch.countDown();
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                log.info("Finalizer thread was interrupted");
                Thread.currentThread().interrupt();
            }
            eventsToConsume.values().stream().filter(x -> x.getAsyncExpireTime() < System.currentTimeMillis()).forEach((Command command) -> {
                try {
                    if (eventsToConsume.remove(command.getCallbackKey()) != null) {
                        long start = System.nanoTime();
                        log.info("Finalization request {}", command.getRqUid());
                        Class<?> callbackClass = Class.forName(command.getCallbackClass());
                        Object callBackBean = context.getBean(callbackClass);
                        Method method = callbackClass.getMethod("onError", String.class, Throwable.class);
                        method.invoke(callBackBean, command.getCallbackKey(), new JaffaRpcExecutionTimeoutException());
                        log.info("Finalization request {} took {}ns", command.getRqUid(), (System.nanoTime() - start));
                    }
                } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                    log.error("Error during finalization command: {}", command);
                }
            });
        }
        log.info("Finalizer thread stopped");
    });

    @SuppressWarnings("squid:S2142")
    public static void startFinalizer(ApplicationContext context) {
        FinalizationWorker.context = context;
        finalizer.start();
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            log.error("Error during FinalizationWorker startup");
            throw new JaffaRpcSystemException(e);
        }
    }

    public static void stopFinalizer() {
        do {
            finalizer.interrupt();
        } while (finalizer.getState() != Thread.State.TERMINATED);
    }
}
