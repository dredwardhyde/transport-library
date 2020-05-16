package com.jaffa.rpc.lib.request;

import com.jaffa.rpc.lib.callbacks.Callback;

import java.util.concurrent.TimeUnit;

public interface Request<T> {
    Request<T> withTimeout(long timeout, TimeUnit unit);

    T executeSync();

    Request<T> onModule(String moduleId);

    void executeAsync(String key, Class<? extends Callback<T>> listener);
}
