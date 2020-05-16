package com.jaffa.rpc.test;

import com.jaffa.rpc.lib.callbacks.Callback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ServiceCallback implements Callback<Void> {


    @Override
    public void onSuccess(String key, Void result) {
        log.info("Key: {}", key);
        log.info("Result: {}", result);
    }

    @Override
    public void onError(String key, Throwable exception) {
        log.error("Exception during async call:", exception);
    }
}
