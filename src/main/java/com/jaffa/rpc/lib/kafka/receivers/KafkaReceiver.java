package com.jaffa.rpc.lib.kafka.receivers;

import com.jaffa.rpc.lib.JaffaService;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.text.MessageFormat;
import java.util.ArrayList;

@Slf4j
public abstract class KafkaReceiver implements Closeable, Runnable {

    private final ArrayList<Thread> threads = new ArrayList<>(JaffaService.getBrokersCount());

    protected void startThreadsAndWait(Runnable runnable) {
        for (int i = 0; i < JaffaService.getBrokersCount(); i++) {
            threads.add(new Thread(runnable));
        }
        threads.forEach(Thread::start);
        threads.forEach(x -> {
            try {
                x.join();
            } catch (InterruptedException e) {
                log.error(MessageFormat.format("Can not join thread {0} in {1}", x.getName(), this.getClass().getSimpleName()), e);
            }
        });
    }

    @Override
    public void close() {
        for (Thread thread : this.threads) {
            do {
                thread.interrupt();
            } while (thread.getState() != Thread.State.TERMINATED);
            log.info("Thread {} from {} terminated", thread.getName(), this.getClass().getSimpleName());
        }
        log.info("{} terminated", this.getClass().getSimpleName());
    }
}
