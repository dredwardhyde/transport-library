package com.jaffa.rpc.lib.request;

import com.jaffa.rpc.lib.callbacks.Callback;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public interface Request<T> {
    Request<T> withTimeout(long timeout, @NotNull TimeUnit unit);

    T executeSync();

    Request<T> onModule(@NotNull String moduleId);

    void executeAsync(@NotNull String key, @NotNull Class<? extends Callback<T>> listener);
}
