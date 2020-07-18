package com.jaffa.rpc.lib.common;

import com.jaffa.rpc.lib.entities.Command;
import com.jaffa.rpc.lib.exception.JaffaRpcExecutionTimeoutException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.*;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FinalizationWorker {

    @Getter
    private static final ConcurrentMap<String, Command> eventsToConsume = new ConcurrentHashMap<>();
    private static ScheduledExecutorService executor;
    private static ApplicationContext context;
    private static final Runnable finalizerThread = () -> eventsToConsume.values()
            .stream()
            .filter(x -> x.getAsyncExpireTime() < System.currentTimeMillis())
            .forEach((Command command) -> {
                try {
                    if (Objects.nonNull(eventsToConsume.remove(command.getCallbackKey()))) {
                        long start = System.nanoTime();
                        log.info("Finalization request {}", command.getRqUid());
                        Class<?> callbackClass = Class.forName(command.getCallbackClass());
                        Method method = callbackClass.getMethod("onError", String.class, Throwable.class);
                        method.invoke(context.getBean(callbackClass), command.getCallbackKey(), new JaffaRpcExecutionTimeoutException());
                        log.info("Finalization request {} took {}ns", command.getRqUid(), (System.nanoTime() - start));
                    }
                } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                    log.error("Error during finalization command: {}", command);
                }
            });

    @SuppressWarnings("squid:S2142")
    public static void startFinalizer(ApplicationContext context) {
        FinalizationWorker.context = context;
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(finalizerThread, 0, 5, TimeUnit.MILLISECONDS);
        log.info("Finalizer thread started");
    }

    public static void stopFinalizer() {
        executor.shutdown();
        log.info("Finalizer thread stopped");
    }
}
