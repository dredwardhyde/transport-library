package com.jaffa.rpc.test.callbacks;

import com.jaffa.rpc.lib.callbacks.Callback;
import com.jaffa.rpc.test.entities.Person;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PersonCallback implements Callback<Person> {

    @Override
    public void onSuccess(String key, Person result) {
        log.info("Received in onSuccess {}", key);
    }

    @Override
    public void onError(String key, Throwable exception) {
        log.info("Received in onError {}", key);
    }
}
